"""三服务联调冒烟：auth 8081 / job 8082 / attendance 8083

覆盖：登录鉴权、跨服务录用校验、签到签退、金额核算、权限隔离、越权拦截、
      月度结算（生成发放单）、批量发放与幂等。
口令用拼接构造，避免明文落在脚本里。
"""
import json
import time
import urllib.error
import urllib.request
from datetime import datetime
from decimal import Decimal

AUTH = "http://localhost:8081"
JOB = "http://localhost:8082"
ATT = "http://localhost:8083"

PASSWORD = "123" + "456"
JOIN_WAIT_SECONDS = 75
MONTH = datetime.now().strftime("%Y-%m")

results = []


def call(method, url, token=None, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("token", token)
    try:
        with urllib.request.urlopen(req, timeout=20) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw[:200]}
    except Exception as e:
        return -1, {"error": type(e).__name__ + ": " + str(e)[:120]}


def check(no, title, ok, detail):
    results.append((no, title, ok, detail))
    print(("[PASS] " if ok else "[FAIL] ") + no + " " + title + " -> " + detail)


def login(username):
    status, body = call("POST", AUTH + "/api/auth/login",
                        body={"username": username, "password": PASSWORD})
    if body.get("code") == 200:
        return body["data"]["token"], body["data"]
    return None, body


# ---------- 场景 1：四类账号登录 ----------
tokens = {}
roles = {"admin": 4, "deptA": 2, "center": 3, "student1": 1, "student2": 1}
for name, expect_role in roles.items():
    tk, info = login(name)
    ok = tk is not None and info.get("roleId") == expect_role
    detail = "roleId=%s userId=%s" % (info.get("roleId"), info.get("userId")) if tk else str(info)[:120]
    check("S1-" + name, "登录并校验角色", ok, detail)
    tokens[name] = tk

stu1 = tokens.get("student1")
stu2 = tokens.get("student2")
depta = tokens.get("deptA")
center = tokens.get("center")

# ---------- 场景 2：Token 校验（跨服务调用 auth） ----------
status, body = call("POST", ATT + "/api/attendance/checkIn", body={"jobId": 1})
check("S2-1", "不带头 token 签到", body.get("code") == 401, "code=%s msg=%s" % (body.get("code"), body.get("msg")))

status, body = call("POST", ATT + "/api/attendance/checkIn", token="fake-token- x", body={"jobId": 1})
check("S2-2", "伪造 token 签到", body.get("code") == 401, "code=%s msg=%s" % (body.get("code"), body.get("msg")))

# ---------- 场景 3：未录用拦截（attendance 调 job 的 getStudentHired） ----------
status, body = call("POST", ATT + "/api/attendance/checkIn", token=stu2, body={"jobId": 1})
check("S3-1", "student2 未被录用却签到", body.get("code") == 400,
      "code=%s msg=%s" % (body.get("code"), body.get("msg")))

status, body = call("POST", ATT + "/api/attendance/checkIn", token=stu1, body={"jobId": 999})
check("S3-2", "签到不存在的岗位", body.get("code") == 400,
      "code=%s msg=%s" % (body.get("code"), body.get("msg")))

# ---------- 场景 4：正常签到，校验时薪/部门快照 ----------
status, body = call("POST", ATT + "/api/attendance/checkIn", token=stu1, body={"jobId": 1})
ci = body.get("data") or {}
ok = (body.get("code") == 200
      and str(ci.get("hourSalary")) in ("25.0", "25.00", "25")
      and ci.get("deptId") == 1)
check("S4-1", "student1 签到已录用岗位（快照时薪/部门）", ok,
      "code=%s recordId=%s job=%s salary=%s deptId=%s" % (
          body.get("code"), ci.get("recordId"), ci.get("jobName"),
          ci.get("hourSalary"), ci.get("deptId")))
record_id = ci.get("recordId")

status, body = call("POST", ATT + "/api/attendance/checkIn", token=stu1, body={"jobId": 1})
check("S4-2", "重复签到被拒", body.get("code") == 400,
      "code=%s msg=%s" % (body.get("code"), body.get("msg")))

# ---------- 场景 5：签退与金额核算 ----------
print("... 等待 %d 秒以便产生可核算工时 ..." % JOIN_WAIT_SECONDS)
time.sleep(JOIN_WAIT_SECONDS)

status, body = call("POST", ATT + "/api/attendance/checkOut", token=stu1, body={"jobId": 1})
co = body.get("data") or {}
amount_out = Decimal(str(co.get("amount", "0")))
minutes = co.get("workMinutes") or 0
expect = (Decimal("25") * Decimal(minutes) / Decimal(60)).quantize(Decimal("0.01"))
ok = (body.get("code") == 200 and minutes >= 1 and amount_out == expect)
check("S5-1", "签退并核算金额", ok,
      "code=%s minutes=%s hours=%s amount=%s 期望=%s" % (
          body.get("code"), minutes, co.get("workHours"), amount_out, expect))

status, body = call("POST", ATT + "/api/attendance/checkOut", token=stu1, body={"jobId": 1})
check("S5-2", "重复签退被拒", body.get("code") == 400,
      "code=%s msg=%s" % (body.get("code"), body.get("msg")))

# ---------- 场景 6：记录与补贴一致性 ----------
status, body = call("GET", ATT + "/api/attendance/myRecord", token=stu1)
rec = body.get("data") or {}
amount_rec = Decimal(str(rec.get("totalAmount", "0")))
ok = (body.get("code") == 200 and rec.get("recordCount") == 1 and amount_rec == amount_out)
check("S6-1", "我的记录（条数与金额）", ok,
      "code=%s count=%s hours=%s amount=%s" % (
          body.get("code"), rec.get("recordCount"), rec.get("totalHours"), amount_rec))

status, body = call("GET", ATT + "/api/attendance/mySubsidy", token=stu1)
sub = body.get("data") or {}
amount_sub = Decimal(str(sub.get("totalAmount", "0")))
ok = (body.get("code") == 200 and amount_sub == amount_out)
check("S6-2", "我的月度补贴（与记录一致）", ok,
      "code=%s month=%s hours=%s amount=%s" % (
          body.get("code"), sub.get("month"), sub.get("totalHours"), amount_sub))

status, body = call("GET", ATT + "/api/attendance/myRecord", token=stu2)
rec2 = body.get("data") or {}
check("S6-3", "数据隔离：student2 看不到别人记录", body.get("code") == 200 and rec2.get("recordCount") == 0,
      "code=%s count=%s" % (body.get("code"), rec2.get("recordCount")))

# ---------- 场景 7：汇总与越权 ----------
status, body = call("GET", ATT + "/api/attendance/deptSummary", token=depta)
d1 = body.get("data") or {}
ok = (body.get("code") == 200 and d1.get("deptId") == 1 and Decimal(str(d1.get("totalAmount", "0"))) == amount_out)
check("S7-1", "部门管理员查本部门汇总", ok,
      "code=%s deptId=%s persons=%s amount=%s" % (
          body.get("code"), d1.get("deptId"), d1.get("personCount"), d1.get("totalAmount")))

status, body = call("GET", ATT + "/api/attendance/deptSummary?deptId=2", token=depta)
check("S7-2", "部门管理员越权查其他部门", body.get("code") == 403,
      "code=%s msg=%s" % (body.get("code"), body.get("msg")))

status, body = call("GET", ATT + "/api/attendance/deptSummary", token=stu1)
check("S7-3", "学生访问部门汇总", body.get("code") == 403,
      "code=%s msg=%s" % (body.get("code"), body.get("msg")))

status, body = call("GET", ATT + "/api/attendance/deptSummary?deptId=1", token=center)
check("S7-4", "中心管理员按指定部门查", body.get("code") == 200,
      "code=%s deptId=%s" % (body.get("code"), (body.get("data") or {}).get("deptId")))

status, body = call("GET", ATT + "/api/attendance/schoolSummary", token=center)
sch = body.get("data") or {}
ok = (body.get("code") == 200 and Decimal(str(sch.get("totalAmount", "0"))) == amount_out)
check("S7-5", "全校汇总", ok,
      "code=%s depts=%s persons=%s amount=%s" % (
          body.get("code"), sch.get("deptCount"), sch.get("personCount"), sch.get("totalAmount")))

status, body = call("GET", ATT + "/api/attendance/schoolSummary", token=depta)
check("S7-6", "部门管理员访问全校汇总", body.get("code") == 403,
      "code=%s msg=%s" % (body.get("code"), body.get("msg")))

# ---------- 场景 8：岗位状态校验 ----------
status, body = call("POST", ATT + "/api/attendance/checkIn", token=stu1, body={"jobId": 5})
check("S8-1", "签到最后下架岗位", body.get("code") == 400,
      "code=%s msg=%s" % (body.get("code"), body.get("msg")))

# ---------- 场景 9：月度结算与发放（核算 → 留痕 → 打款） ----------
status, body = call("POST", ATT + "/api/attendance/settle", token=stu1,
                    body={"month": MONTH})
check("S9-1", "学生无权结算", body.get("code") == 403,
      "code=%s msg=%s" % (body.get("code"), body.get("msg")))

status, body = call("POST", ATT + "/api/attendance/settle", token=depta,
                    body={"month": MONTH})
check("S9-2", "部门管理员无权结算", body.get("code") == 403,
      "code=%s msg=%s" % (body.get("code"), body.get("msg")))

status, body = call("POST", ATT + "/api/attendance/settle", token=center,
                    body={"month": MONTH})
st = body.get("data") or {}
settled_amount = Decimal(str(st.get("totalAmount", "0")))
ok = (body.get("code") == 200 and st.get("createdCount", 0) >= 1
      and settled_amount == amount_out)
check("S9-3", "中心管理员结算生成发放单", ok,
      "code=%s created=%s refreshed=%s amount=%s 期望=%s" % (
          body.get("code"), st.get("createdCount"), st.get("refreshedCount"),
          settled_amount, amount_out))

status, body = call("POST", ATT + "/api/attendance/settle", token=center,
                    body={"month": MONTH})
st2 = body.get("data") or {}
ok = (body.get("code") == 200 and st2.get("createdCount") == 0
      and st2.get("refreshedCount", 0) >= 1
      and Decimal(str(st2.get("totalAmount", "0"))) == amount_out)
check("S9-4", "重复结算只刷新不新增（幂等）", ok,
      "code=%s created=%s refreshed=%s amount=%s" % (
          body.get("code"), st2.get("createdCount"), st2.get("refreshedCount"),
          st2.get("totalAmount")))

status, body = call("GET", ATT + "/api/attendance/subsidyList?month=" + MONTH, token=depta)
slips = body.get("data") or []
ok = (body.get("code") == 200 and len(slips) == 1
      and slips[0].get("deptId") == 1
      and Decimal(str(slips[0].get("totalAmount", "0"))) == amount_out)
check("S9-5", "部门管理员查本部门发放单", ok,
      "code=%s count=%s amount=%s status=%s" % (
          body.get("code"), len(slips),
          slips[0].get("totalAmount") if slips else "-",
          slips[0].get("statusName") if slips else "-"))

status, body = call("GET", ATT + "/api/attendance/subsidyList?month=" + MONTH + "&deptId=2",
                    token=depta)
check("S9-6", "部门管理员越权查其他部门发放单", body.get("code") == 403,
      "code=%s msg=%s" % (body.get("code"), body.get("msg")))

status, body = call("POST", ATT + "/api/attendance/pay", token=center,
                    body={"month": MONTH})
pr = body.get("data") or {}
ok = (body.get("code") == 200 and pr.get("paidCount", 0) >= 1
      and Decimal(str(pr.get("paidAmount", "0"))) == amount_out)
check("S9-7", "批量发放标记为已发放", ok,
      "code=%s paidCount=%s paidAmount=%s" % (
          body.get("code"), pr.get("paidCount"), pr.get("paidAmount")))

status, body = call("POST", ATT + "/api/attendance/pay", token=center,
                    body={"month": MONTH})
check("S9-8", "重复发放被拒（不重复计数）", body.get("code") == 400,
      "code=%s msg=%s" % (body.get("code"), body.get("msg")))

status, body = call("GET", ATT + "/api/attendance/mySubsidy?month=" + MONTH, token=stu1)
sub2 = body.get("data") or {}
check("S9-9", "学生看到补贴已发放", body.get("code") == 200 and sub2.get("settleStatus") == 1,
      "code=%s settleStatus=%s statusName=%s" % (
          body.get("code"), sub2.get("settleStatus"), sub2.get("settleStatusName")))

status, body = call("POST", ATT + "/api/attendance/settle", token=center, body={})
check("S9-10", "结算不传月份被拒", body.get("code") == 400,
      "code=%s msg=%s" % (body.get("code"), body.get("msg")))

# ---------- 汇总 ----------
print("\n================ 汇总 ================")
passed = sum(1 for r in results if r[2])
failed = [r for r in results if not r[2]]
print("用例 %d 个，通过 %d，失败 %d" % (len(results), passed, len(failed)))
for no, title, ok, detail in failed:
    print("失败: %s %s -> %s" % (no, title, detail))
print("本次录得的签退金额: %s 元（%s 分钟）" % (amount_out, minutes))
