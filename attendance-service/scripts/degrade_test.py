"""降级验证：岗位服务不可用时，工时服务应拒绝业务写操作并返回 500，
读操作（查询本地工时）不受影响。口令用拼接构造。"""
import json
import urllib.error
import urllib.request

AUTH = "http://localhost:8081"
ATT = "http://localhost:8083"
PASSWORD = "123" + "456"


def call(method, url, token=None, body=None):
    data = json.dumps(body).encode("utf-8") if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("token", token)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8", "replace")
        try:
            return e.code, json.loads(raw)
        except Exception:
            return e.code, {"raw": raw[:200]}
    except Exception as e:
        return -1, {"error": type(e).__name__ + ": " + str(e)[:120]}


status, body = call("POST", AUTH + "/api/auth/login",
                    body={"username": "student1", "password": PASSWORD})
token = body["data"]["token"]
print("登录成功 userId=%s" % body["data"]["userId"])

status, body = call("POST", ATT + "/api/attendance/checkIn", token=token, body={"jobId": 1})
print("D1 岗位服务离线时签到 -> http=%s code=%s msg=%s" % (status, body.get("code"), body.get("msg")))

status, body = call("GET", ATT + "/api/attendance/myRecord", token=token)
print("D2 岗位服务离线时查我的记录 -> http=%s code=%s recordCount=%s"
      % (status, body.get("code"), (body.get("data") or {}).get("recordCount")))

status, body = call("GET", ATT + "/api/attendance/mySubsidy", token=token)
print("D3 岗位服务离线时查我的补贴 -> http=%s code=%s amount=%s"
      % (status, body.get("code"), (body.get("data") or {}).get("totalAmount")))
