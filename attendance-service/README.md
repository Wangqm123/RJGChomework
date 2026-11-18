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

```bash
# 1. 先启权限服务（仓库根目录）
mvn spring-boot:run

# 2. 再启岗位服务
cd job-service && mvn spring-boot:run

# 3. 最后启本服务
cd attendance-service && mvn spring-boot:run
```

启动成功后：本服务 `http://localhost:8083`，数据控制台 `http://localhost:8083/h2-console`
（JDBC URL `jdbc:h2:file:./data/attendancedb`，用户 sa，密码空）。

运行单元测试（20 个用例，使用独立内存库，不影响开发数据）：

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
| `/api/attendance/checkIn` | POST | 签到 `{jobId}` | 学生本人 | `{recordId, jobName, hourSalary, checkInTime, status}` |
| `/api/attendance/checkOut` | POST | 签退 `{jobId}` | 学生本人 | `{workMinutes, workHours, amount, status, tip}` |
| `/api/attendance/myRecord` | GET | 我的记录 `?month=2026-11` | 学生本人 | `{month, recordCount, settledCount, pendingCount, totalHours, totalAmount, list}` |
| `/api/attendance/mySubsidy` | GET | 我的月度补贴 `?month=2026-11` | 学生本人 | `{userName, month, recordCount, totalHours, totalAmount}` |
| `/api/attendance/deptSummary` | GET | 部门汇总 `?month=&deptId=` | 部门管理员（本部门）/ 中心管理员（需指定 deptId） | `{deptId, personCount, totalHours, totalAmount, list}` |
| `/api/attendance/schoolSummary` | GET | 全校汇总 `?month=` | 助学中心管理员 | `{deptCount, personCount, totalHours, totalAmount, list}` |

`month` 不传默认**当月**，格式必须是 `yyyy-MM`，否则 400。

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

### 5.3 核算口径（答辩口径以此为准）

```
工时（小时）= 分钟数 ÷ 60，保留 1 位小数（仅展示）
金额（元）  = 分钟数 × 该条记录的时薪快照 ÷ 60，保留 2 位小数（精确到分）
月度补贴    = Σ 该月 status=1 的记录金额
```

- 金额用**精确分钟**算，不是「先四舍五入工时再乘时薪」，否则 10 分钟的零头会累计出偏差；
- 一律 `BigDecimal`，不用 `double`；
- 只统计已签退（status=1），未签退和异常待确认不计入，避免学生误以为已经算钱。

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
└── src
    ├── main
    │   ├── java/com/campus/attendance
    │   │   ├── AttendanceServiceApplication.java   # 启动类（8083）
    │   │   ├── controller/AttendanceController.java # 6 个接口入口
    │   │   ├── service/AttendanceService.java       # 签到/签退/我的记录
    │   │   ├── service/SubsidyService.java          # 补贴核算 + 部门/全校汇总
    │   │   ├── service/AuthGuard.java               # Token 校验 + 角色/部门拦截
    │   │   ├── client/AuthClient.java               # 调权限服务（checkToken/getUserInfo）
    │   │   ├── client/JobClient.java                # 调岗位服务（getStudentHired/getJob）
    │   │   ├── entity/AttendanceRecord.java         # 签到记录实体
    │   │   ├── repository/…                         # JPA 数据访问
    │   │   ├── dto/…                                # 请求/响应对象
    │   │   ├── common/…                             # 统一返回、异常、状态字典、月份区间、金额计算
    │   │   └── config/RestTemplateConfig.java       # HTTP 客户端超时配置
    │   └── resources
    │       ├── application.yml / application-mysql.yml
    │       ├── schema.sql                           # 建表语句
    │       └── data.sql                             # 造数说明（不预置记录）
    └── test/java/com/campus/attendance
        └── AttendanceServiceTests.java              # 20 个单元测试
```

## 九、技术说明

- **跨服务鉴权**：本服务不解析 JWT，统一调权限服务 `checkToken`，Token 逻辑只在一处维护；
- **超时保护**：`RestTemplate` 连接 3s / 读取 5s，对方服务卡死不会拖垮本服务；
- **快照设计**：签到即冻结时薪与部门，历史工资不受调薪影响，汇总也不必跨服务查询；
- **金额精度**：`BigDecimal` + 精确分钟计算，避免浮点误差与"明细加总 ≠ 汇总"；
- **半开区间查月**：用 `[月初, 下月初)` 而不是 `BETWEEN`，不会漏掉月末最后一秒的记录；
- **测试隔离**：单元测试用内存库 + `@Transactional` 回滚，两个外部服务用 `@MockBean` 打桩，不依赖网络；
- **数据库可切换**：默认 H2，启动加 `--spring.profiles.active=mysql` 即切 MySQL（注意 `data.sql` 的 `MERGE` 语法需改写）。
