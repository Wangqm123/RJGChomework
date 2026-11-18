# 工时考勤与补贴核算服务（attendance-service）

校园勤工助学岗位分布式管理系统 · 三大服务之三，负责**签到签退 → 工时统计 → 月度补贴核算 → 部门/全校汇总**。

> 本服务由成员 5 负责开发，与权限服务（8081）、岗位服务（8082）平级，
> 是同一仓库内的独立 Maven 工程（目录 `attendance-service/`），可单独构建、单独部署。

## 一、这个服务是干什么的

| 能力 | 说明 |
|---|---|
| 学生签到 | 校验 Token + 校验是否被该岗位录用，两关都过才允许打卡，并快照时薪与部门 |
| 学生签退 | 回填工时，单次超 12 小时标记「异常待确认」，需核实后才计薪 |
| 我的记录 | 按月查自己的签到记录与当月合计 |
| 月度补贴 | 按「工时 × 当时时薪」核算，精确到分 |
| **月度结算** | 把某月已签退工时固化成**发放单**（`t_subsidy`），金额留痕可回溯 |
| **补贴发放** | 批量把待发放单标记为已发放，金额冻结；重复点不重复计数 |
| 部门汇总 | 部门管理员看本部门，按学生下钻；中心管理员可指定部门查 |
| 全校汇总 | 助学中心管理员看，按部门下钻 |

身份校验**不在本服务做**：每次请求从请求头 `token` 取出 Token，
调用权限服务 `/api/auth/checkToken` 校验；录用资格调岗位服务 `/api/job/getStudentHired`。

## 二、端口与依赖

| 项 | 值 |
|---|---|
| 端口 | **8083**（全组约定：auth=8081 / job=8082 / attendance=8083） |
| 依赖服务 | 权限服务 `http://localhost:8081`（`auth.service.base-url`）、岗位服务 `http://localhost:8082`（`job.service.base-url`） |
| 数据库 | 默认 H2 文件库 `./data/attendancedb`（零安装），可切 MySQL |
| JDK | 17+（父工程 Spring Boot 3.3.5） |

**启动顺序：权限服务 → 岗位服务 → 本服务**。
任一依赖服务未启动时，需要跨服务校验的接口返回 500 +「XX 服务暂不可用」的友好提示，
**不会放行**签到（宁可打不了卡，也不能让未录用的人混进来）。

## 三、怎么跑起来

> ⚠️ **联调/演示前必做：清理上一轮的本地数据**
> 本服务用 H2 文件库 `./data/attendancedb.mv.db`。上一轮若留下「已签到未签退」的记录，
> 下次签到时会被"关 3"直接拦下，表现出来就是**莫名返回 400「您在该岗位已签到且未签退」**。
> 删掉数据目录即可，启动时会按 `schema.sql` 自动重建：
>
> ```bash
> cd attendance-service
> rm -rf data          # Windows 下直接删除 data 文件夹
> ```

**推荐启动方式（已实测，中文路径下同样可用）**：

```bash
# 每个服务都在自己目录下打包后运行
mvn -DskipTests package
java -jar target/attendance-service-1.0.0.jar
```

三个服务的启动顺序与目录：

| 顺序 | 服务 | 目录 | 端口 |
|---|---|---|---|
| 1 | 权限服务 | 仓库根目录 | 8081 |
| 2 | 岗位服务 | `job-service/` | 8082 |
| 3 | 本服务 | `attendance-service/` | 8083 |

> 已知问题：项目路径含中文时 `mvn spring-boot:run` 会报 `ClassNotFoundException`
> （spring-boot-maven-plugin 的 fork 编码问题，详见《测试与缺陷记录》BUG-03）。
> 上面用 jar 启动即可绕开；路径改为纯英文后 `mvn spring-boot:run` 也能正常工作。

启动成功后：本服务 `http://localhost:8083`，数据控制台 `http://localhost:8083/h2-console`
（JDBC URL 必须与配置完全一致：`jdbc:h2:file:./data/attendancedb;MODE=MySQL;AUTO_SERVER=TRUE`，
用户 sa，密码空；少了 `MODE/AUTO_SERVER` 两个参数会因文件锁连不上）。

运行单元测试（39 个用例，使用独立内存库，不影响开发数据）：

```bash
mvn test
```

## 四、接口一览

统一返回格式（与权限服务、岗位服务一致）：

```json
{ "code": 200, "msg": "操作成功", "data": {} }
```

错误码：**200** 成功 / **400** 参数错误或业务规则冲突 / **401** 未登录或 Token 失效 /
**403** 权限不足 / **500** 内部异常（含依赖服务不可用）。
需要登录的接口把 Token 放请求头：`token: <登录拿到的 JWT>`。

| 接口 | 方式 | 说明 | 权限 | 返回 data |
|---|---|---|---|---|
| `/api/attendance/checkIn` | POST | 签到 `{jobId}` | 学生本人 | `{recordId, jobId, jobName, deptId, hourSalary, checkInTime, status, statusName}` |
| `/api/attendance/checkOut` | POST | 签退 `{jobId}` | 学生本人 | `{recordId, jobId, checkInTime, checkOutTime, workMinutes, workHours, amount, status, statusName, tip}` |
| `/api/attendance/myRecord` | GET | 我的记录 `?month=2026-11` | 学生本人 | `{month, recordCount, settledCount, pendingCount, totalHours, totalAmount, list}` |
| `/api/attendance/mySubsidy` | GET | 我的月度补贴 `?month=2026-11` | 学生本人 | `{userId, userName, month, recordCount, totalHours, totalAmount, settleStatus, settleStatusName}` |
| `/api/attendance/deptSummary` | GET | 部门汇总 `?month=&deptId=` | 部门管理员（本部门）/ 中心管理员（需指定 deptId） | `{deptId, month, personCount, totalHours, totalAmount, list}` |
| `/api/attendance/schoolSummary` | GET | 全校汇总 `?month=` | 助学中心管理员 | `{month, deptCount, personCount, totalHours, totalAmount, list}` |
| `/api/attendance/settle` | POST | 月度结算 `{month, deptId?, remark?}` | 助学中心管理员 | `{month, deptId, userCount, createdCount, refreshedCount, skippedCount, totalHours, totalAmount, list}` |
| `/api/attendance/pay` | POST | 批量发放 `{month, deptId?}` | 助学中心管理员 | `{month, deptId, paidCount, paidAmount, tip}` |
| `/api/attendance/subsidyList` | GET | 发放单列表 `?month=&deptId=&status=` | 部门管理员（本部门）/ 中心管理员（可全校） | `[{subsidyId, userId, userName, deptId, month, recordCount, totalHours, totalAmount, status, statusName, settleTime, payTime}]` |

`month` 不传默认**当月**（`settle` / `pay` 例外：**必须显式传**，避免误操作把本月账结掉），
格式必须是 `yyyy-MM`，否则 400。

几个统计字段口径不同，别混用：

- `myRecord.recordCount` = 当月**全部**记录数（含未签退、异常待确认），另有 `settledCount/pendingCount` 拆开计数；
- `mySubsidy.recordCount` = 当月**已签退（status=1）**、真正参与计薪的记录数；
- `settle.userCount` = 结算对象数，按「人 × 部门」计：一个人在两个部门打工算 **2**（对应 2 张发放单）；
- `settle.totalHours/totalAmount` 只累计**本次实际写入**的单，`skippedCount`（已发放、金额冻结）不计入。

### 4.1 请求示例

```bash
# 0. 登录拿 Token（演示账号密码见总 README）
curl -X POST http://localhost:8081/api/auth/login -H "Content-Type: application/json" \
  -d '{"username":"student1","password":"<密码>"}'

# 1. 学生签到（student1 已被岗位 1 录用）
curl -X POST http://localhost:8083/api/attendance/checkIn \
  -H "Content-Type: application/json" -H "token: <student1的token>" \
  -d '{"jobId":1}'

# 2. 签退
curl -X POST http://localhost:8083/api/attendance/checkOut \
  -H "Content-Type: application/json" -H "token: <student1的token>" \
  -d '{"jobId":1}'

# 3. 我的工时与补贴
curl "http://localhost:8083/api/attendance/mySubsidy?month=2026-11" -H "token: <student1的token>"

# 4. 部门管理员查本部门汇总（deptId 不用传，从权限服务取）
curl "http://localhost:8083/api/attendance/deptSummary?month=2026-11" -H "token: <deptA的token>"

# 5. 助学中心管理员查全校汇总
curl "http://localhost:8083/api/attendance/schoolSummary?month=2026-11" -H "token: <center的token>"

# 6. 助学中心管理员结算（全校，也可加 "deptId":1 只结一个部门）
curl -X POST http://localhost:8083/api/attendance/settle \
  -H "Content-Type: application/json" -H "token: <center的token>" \
  -d '{"month":"2026-11"}'

# 7. 查发放单（部门管理员只看本部门；中心管理员不传 deptId 看全校）
curl "http://localhost:8083/api/attendance/subsidyList?month=2026-11&status=0" -H "token: <center的token>"

# 8. 批量发放（把待发放标记为已发放，重复调用返回 400）
curl -X POST http://localhost:8083/api/attendance/pay \
  -H "Content-Type: application/json" -H "token: <center的token>" \
  -d '{"month":"2026-11"}'
```

## 五、业务规则与核算口径

### 5.1 签到校验链路（核心，需求文档 3.3）

```
1. 请求头有 token 吗？            → 没有：401
2. 调权限服务 checkToken          → 无效：401，拿到 userId
3. 角色是学生吗？                  → 不是：403
4. 调岗位服务 getJob              → 岗位不存在：400
5. 岗位已下架（status=5）？        → 是：400
6. 调岗位服务 getStudentHired     → false：400「您未被该岗位录用，无法签到」
7. 本岗位已有未签退记录？          → 有：400「请先签退」
8. 全部通过 → 落库，并快照 jobName / hourSalary / deptId
```

### 5.2 状态字典

```
0 已签到未签退 → 1 已签退（计薪） / 2 异常待确认（不计薪，需人工核实）
单次工时 > 12 小时 → 自动置为 2，remark 记录原因
```

> 接口文档只定义了"标记异常"，没有定义"核实通过"接口，因此本服务只负责标记并把它排除在
> 计薪之外；管理员核实后由运维直接修正该条记录状态（2 → 1），再重新结算即可。
> 这是当前范围外的已知留白，答辩被问到时按此口径回答，不要说"系统会自动确认"。

### 5.3 核算口径（答辩口径以此为准）

```
工时（小时）= 分钟数 ÷ 60，保留 1 位小数（仅展示）
金额（元）  = 分钟数 × 该条记录的时薪快照 ÷ 60，保留 2 位小数（精确到分）
月度补贴    = Σ 该月 status=1 的记录金额
```

- 金额用**精确分钟**算，不是「先四舍五入工时再乘时薪」，否则 10 分钟的零头会累计出偏差；
- 一律 `BigDecimal`，不用 `double`；
- 只统计已签退（status=1），未签退和异常待确认不计入，避免学生误以为已经算钱。

**结算 ≠ 实时核算**（答辩常被追问）：

| | 实时核算（mySubsidy / deptSummary） | 结算（settle → 发放单） |
|---|---|---|
| 是否落库 | 不落库，每次现算 | 写入 `t_subsidy`，留痕 |
| 数据变化 | 补录工时后金额立刻变 | 待发放可刷新；**已发放冻结** |
| 用途 | 学生/管理员随时看账 | 对账、打款凭证 |

```
结算金额 = Σ(该人该部门该月 status=1 的记录金额)   ← 与实时核算口径完全一致
一人一月一部门一张单，唯一键 (user_id, dept_id, settle_month)
已发放的单再次结算 → 跳过（skippedCount++），金额不改
```

按部门拆单而不是"一人一月一张"：一个人跨部门打工时，各部门各自结算，
这样**部门汇总金额 = 该部门发放单合计**，部门对账不会串。

### 5.4 为什么存时薪快照

签到时把 `jobName`、`hourSalary`、`deptId` 一起写进签到记录：

1. 分布式服务各自独立数据库，不能跨库 join，汇总时不用逐条回调岗位服务；
2. 岗位时薪日后会调整，历史工资必须按**当时**的时薪结算；
3. 岗位招满/下架后 `listJob` 查不到该岗位，但历史工时仍要结算。

### 5.5 权限矩阵

| 接口 | 学生(1) | 部门管理员(2) | 助学中心(3) | 系统管理员(4) |
|---|---|---|---|---|
| checkIn / checkOut | ✅ 本人 | ❌ 403 | ❌ 403 | ❌ 403 |
| myRecord / mySubsidy | ✅ 本人 | ❌ 403 | ❌ 403 | ❌ 403 |
| deptSummary | ❌ 403 | ✅ 本部门 | ✅ 需指定 deptId | ❌ 403 |
| schoolSummary | ❌ 403 | ❌ 403 | ✅ | ❌ 403 |
| settle（结算） | ❌ 403 | ❌ 403 | ✅ | ❌ 403 |
| pay（发放） | ❌ 403 | ❌ 403 | ✅ | ❌ 403 |
| subsidyList | ❌ 403 | ✅ 本部门 | ✅ 全校/指定部门 | ❌ 403 |

**为什么部门管理员不能结算/发放**：钱由助学中心统一核算与打款，
部门只负责用工与核对工时；结算与发放都只开放给中心管理员，
避免"部门自己报工时自己发钱"的越权风险。

部门管理员的 `deptId` **一律从权限服务取**，前端传别的部门直接 403（防越权）。

## 六、异常与错误码

| 场景 | 返回 |
|---|---|
| Token 缺失 / 失效 | 401 |
| 非学生调签到签退 / 冒用身份 | 403 |
| 未被该岗位录用就签到 | 400「您未被该岗位录用，无法签到」 |
| 同一岗位重复签到（未签退） | 400「您在该岗位已签到且未签退，请先签退」 |
| 没签到直接签退 | 400「未找到该岗位的签到记录，请先签到」 |
| 签到不足 1 分钟就签退 | 400 |
| 岗位已下架 | 400「岗位已下架，无法签到」 |
| 岗位不存在 | 400「岗位不存在」（透传岗位服务提示） |
| 月份格式错（如 2026-13） | 400「月份格式应为 yyyy-MM，例如 2026-11」 |
| 部门管理员查其他部门 | 403「只能查看本部门的工时汇总」 |
| 中心管理员查部门汇总不传 deptId | 400 |
| 该月没有可结算工时 | 400「该月没有可结算的已签退工时记录」 |
| 结算不传月份 / 格式错 | 400 |
| 重复发放（已无待发放单） | 400「该月没有待发放的补贴单」 |
| 并发结算导致唯一键冲突 | 400「本月结算正在被其他操作执行，请稍后重试」 |
| 权限服务 / 岗位服务不可用 | 500「XX 服务暂不可用，请稍后重试」 |

## 七、预置数据

本服务**不预置签到记录**（工时数据必须与岗位服务的录用关系对得上，硬编码容易造假数据）。演示时这样造数：

1. 登录 `student1` 拿 Token（userId=4）
2. 岗位服务预置：`student1` 已被岗位 1「图书馆图书整理助理」录用，时薪 25 元、部门 1
3. `checkIn {"jobId":1}` → 等几分钟 → `checkOut {"jobId":1}`
4. 查 `mySubsidy?month=当月` 即可看到工时与金额

## 八、项目结构

```
attendance-service
├── pom.xml
├── README.md
├── 演示脚本.md                                    # 现场演示 9 步（成员 6 用）
├── 测试与缺陷记录.md                              # 39 条用例 + 4 个缺陷的复现与回归
├── scripts
│   ├── smoke_test.py                              # 三服务联调 33 场景（含结算与发放）
│   └── degrade_test.py                            # 依赖服务离线降级验证
└── src
    ├── main
    │   ├── java/com/campus/attendance
    │   │   ├── AttendanceServiceApplication.java   # 启动类（8083）
    │   │   ├── controller/AttendanceController.java # 9 个接口入口
    │   │   ├── service/AttendanceService.java       # 签到/签退/我的记录
    │   │   ├── service/SubsidyService.java          # 实时核算 + 部门/全校汇总
    │   │   ├── service/SettlementService.java       # 月度结算 + 批量发放 + 发放单查询
    │   │   ├── service/AuthGuard.java               # Token 校验 + 角色/部门拦截
    │   │   ├── client/AuthClient.java               # 调权限服务（checkToken/getUserInfo）
    │   │   ├── client/JobClient.java                # 调岗位服务（getStudentHired/getJob）
    │   │   ├── entity/AttendanceRecord.java         # 签到记录实体（含并发排他键）
    │   │   ├── entity/Subsidy.java                  # 月度发放单实体
    │   │   ├── repository/…                         # JPA 数据访问（含条件更新发放）
    │   │   ├── dto/…                                # 请求/响应对象
    │   │   ├── common/…                             # 统一返回、异常、状态字典、月份区间、金额计算
    │   │   └── config/RestTemplateConfig.java       # HTTP 客户端超时配置
    │   └── resources
    │       ├── application.yml / application-mysql.yml
    │       ├── schema.sql                           # 建表语句（含并发约束与发放单唯一键）
    │       └── data.sql                             # 造数说明（不预置记录）
    └── test/java/com/campus/attendance
        ├── AttendanceServiceTests.java              # 22 个业务规则用例
        ├── AttendanceConcurrencyTests.java          # 1 个并发签到用例（不加事务，需真实提交）
        └── SettlementServiceTests.java              # 16 个结算/发放用例（TC-24~TC-39）
```

## 九、技术说明

- **跨服务鉴权**：本服务不解析 JWT，统一调权限服务 `checkToken`，Token 逻辑只在一处维护；
- **超时保护**：`RestTemplate` 连接 3s / 读取 5s，对方服务卡死不会拖垮本服务；
- **快照设计**：签到即冻结时薪与部门，历史工资不受调薪影响，汇总也不必跨服务查询；
- **金额精度**：`BigDecimal` + 精确分钟计算，避免浮点误差与"明细加总 ≠ 汇总"；
- **并发防重**：`active_key`（未签退时 = `userId:jobId`，签退置 NULL）+ 唯一约束 `uk_att_active`，
  由数据库拦下并发重复签到；业务层先查重只作友好提示。选择这一方案而不是进程内锁，
  是因为它天然支持多实例部署，且不影响同一岗位的历史多次打卡记录。
- **结算幂等**：发放单唯一键 `(user_id, dept_id, settle_month)`，重复结算只能刷新不能新增；
  并发结算由唯一键兜底，冲突时整体回滚并提示重试，不会出现两张单重复发钱。
- **发放幂等**：用条件更新 `UPDATE ... WHERE status=0`，只有仍为待发放的才改成已发放，
  按影响行数统计发放人数——两个管理员同时点发放，第二次影响 0 行，不会重复计数，也不需要悲观锁。
- **半开区间查月**：用 `[月初, 下月初)` 而不是 `BETWEEN`——JPQL 里显式写 `>= :from AND < :to`
  （派生查询的 `Between` 生成的是两端闭合的 SQL，恰好落在下月 1 日 00:00 的记录会被两个月重复统计），
  既不会漏掉月末最后一秒，也不会把下月第一刻算进本月；
- **测试隔离**：单元测试用内存库 + `@Transactional` 回滚，两个外部服务用 `@MockBean` 打桩，不依赖网络；
- **数据库可切换**：默认 H2，启动加 `--spring.profiles.active=mysql` 即切 MySQL。
  本服务 `data.sql` 只有一条 `SELECT 1;` 占位（不预置业务数据），可直接在 MySQL 执行；
  切库前唯一要处理的是 `schema.sql` 里 3 行 H2 专有的 `CREATE INDEX IF NOT EXISTS`，
  注释掉后启动，再手工 `ALTER TABLE ... ADD INDEX` 建索引（详见 `application-mysql.yml` 顶部说明）。

## 十、联调验证（已执行，33/33 通过）

三个服务同时在线后，可直接跑仓库内附的联调脚本，无需 Postman：

```bash
# 前置：三个服务已启动（8081 / 8082 / 8083），且已按第三节清过 data 目录
python scripts/smoke_test.py     # 33 个场景：鉴权、未录用拦截、签到签退、金额、越权、下架岗位、结算与发放
python scripts/degrade_test.py   # 降级：需先手动停掉 8082，验证「写操作拒绝、读操作可用」
```

`smoke_test.py` 在签到与签退之间等待 75 秒，是为了产生可核算的工时（签退要求满 1 分钟）。

逐条结果与截图级证据见《测试与缺陷记录.md》第三节，要点：

| 验证点 | 结果 |
|---|---|
| 登录鉴权、角色识别 | 5 类账号全部正确 |
| 跨服务录用校验 | student2 未录用签到 → 400「您未被该岗位录用」 |
| Token 校验（调 8081） | 无 token / 伪造 token → 401 |
| 时薪快照 | 签到返回 hourSalary=25.0、deptId=1 |
| 金额核算 | 1 分钟 × 25 元/时 = 0.42 元，明细与汇总一致 |
| 权限隔离与越权 | 学生 403、部门管理员跨部门 403、中心管理员全校可查 |
| 结算生成发放单 | 中心管理员结算成功，金额与实时核算一致；重复结算只刷新不新增 |
| 发放与幂等 | 发放后 `paidCount` 正确，重复发放 400，学生端 `settleStatus` 变为已发放 |
| 依赖服务降级 | 停 8082 后签到 500 且不放行，查询类接口仍 200 |
