# 岗位申请与审核服务（job-service）

校园勤工助学岗位分布式管理系统 · 三大服务之二，负责**岗位生命周期**与**投递/录用**的全流程。

> 本服务由成员 4 负责开发；成员 3 的用户权限服务（auth-service）与本服务平级，
> 仓库根目录即为 auth-service 工程，本服务是同一仓库内的独立 Maven 工程（目录 `job-service/`），可单独构建、单独部署。

## 一、这个服务是干什么的

| 能力 | 说明 |
|---|---|
| 岗位发布 | 部门管理员填写岗位信息提交，进入"待中心审核" |
| 岗位审核 | 助学中心管理员通过（上架）/驳回（必须填理由） |
| 岗位浏览 | 学生查看已上架岗位（分页） |
| 岗位投递 | 学生投递，同一岗位只能投递一次 |
| 申请审核 | 部门管理员录用/拒绝，录用满额岗位自动"招满" |
| 状态流转 | 修改、下架本部门岗位 |
| 对外查询 | 给工时服务提供"学生是否被该岗位录用"接口 |

身份校验**不在本服务做**：每次需要登录的请求，从请求头 `token` 取出 Token，
调用权限服务 `/api/auth/checkToken` 校验，再按角色/部门做权限拦截（分布式服务解耦）。

## 二、端口与依赖

| 项 | 值 |
|---|---|
| 端口 | **8082**（全组约定：auth=8081 / job=8082 / attendance=8083） |
| 依赖服务 | 权限服务 `http://localhost:8081`（配置项 `auth.service.base-url`） |
| 数据库 | 默认 H2 文件库 `./data/jobdb`（零安装），可切 MySQL |
| JDK | 17+（父工程 Spring Boot 3.3.5） |

**启动顺序：先启权限服务，再启本服务**。权限服务没启动时，
本服务的登录类接口会返回 500 +「权限服务暂不可用」的友好提示，不会崩溃。

## 三、怎么跑起来

```bash
# 命令行（在 job-service 目录下）
mvn spring-boot:run
```

或 VS Code / IDEA 打开 `job-service` 目录，运行 `JobServiceApplication`。
启动成功后：本服务 `http://localhost:8082`，数据控制台 `http://localhost:8082/h2-console`
（JDBC URL `jdbc:h2:file:./data/jobdb`，用户 sa，密码空）。

首次启动会自动执行 `schema.sql`（建表）+ `data.sql`（预置演示数据），幂等可重复启动。

运行单元测试（33 个用例，使用独立内存库，不影响开发数据）：

```bash
mvn test
```

## 四、接口一览

统一返回格式（与权限服务一致）：

```json
{ "code": 200, "msg": "操作成功", "data": {} }
```

错误码：**200** 成功 / **400** 参数错误或业务规则冲突 / **401** 未登录或 Token 失效 /
**403** 权限不足 / **500** 内部异常（含权限服务不可用）。
需要登录的接口把 Token 放请求头：`token: <登录拿到的 JWT>`。

### 4.1 接口文档初稿约定的 7 个接口

| 接口 | 方式 | 说明 | 权限 | 返回 data |
|---|---|---|---|---|
| `/api/job/publish` | POST | 发布岗位 | 部门管理员 | `{jobId}` |
| `/api/job/auditJob` | POST | 助学中心审核岗位 | 助学中心管理员 | `{status, statusName}` |
| `/api/job/listJob` | GET | 已上架岗位列表（page/size） | 公开 | `{list, total}` |
| `/api/job/applyJob` | POST | 学生投递岗位 | 学生本人 | `{applyId}` |
| `/api/job/auditApply` | POST | 部门管理员审核申请 | 部门管理员（本部门） | `{status, statusName, jobStatus, jobStatusName}` |
| `/api/job/getStudentHired` | GET | 是否被该岗位录用（工时服务调用） | 服务间调用 | `true` / `false` |
| `/api/job/listMyApply` | GET | 我的申请记录 | 学生本人 | 申请列表（含申请人 userId、岗位信息、审核状态） |

### 4.2 按《需求文档》和工时服务联调补充的 4 个接口（接口文档初稿未列出）

| 接口 | 方式 | 说明 | 权限 |
|---|---|---|---|
| `/api/job/updateJob` | POST | 修改岗位（被驳回的岗位修改后重新送审） | 部门管理员（本部门） |
| `/api/job/offlineJob` | POST | 下架岗位（JSON body 传 `jobId`） | 部门管理员（本部门） |
| `/api/job/listApply` | GET | 查看某岗位的申请列表（`?jobId=`，含申请人 userId） | 部门管理员（本部门）/ 助学中心管理员 |
| `/api/job/getJob` | GET | 岗位详情，任意状态（`?jobId=`），供工时服务核算补贴 | 服务间调用（无需 Token） |

> 前 3 个对应需求文档 DeptAdmin-2「修改/下架岗位」和 3.2「申请记录查询」；
> 最后一个 `getJob` 是工时服务联调时提出的缺口：`listJob` 只返回已上架岗位，
> 而岗位招满/下架后学生历史工时仍要结算，需要能随时取到 `hourSalary`、`deptId`、`status`。
> 这四个接口均未出现在接口文档初稿中，后续可请成员 2 补进正式文档。

### 4.3 请求示例

```bash
# 0. 先在权限服务登录，拿到 Token（演示账号密码均为 123456）
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"deptA","password":"123456"}'

# 1. 部门管理员发布岗位
curl -X POST http://localhost:8082/api/job/publish \
  -H "Content-Type: application/json" -H "token: <deptA的token>" \
  -d '{"jobName":"自习室值守","address":"图书馆三楼","hourSalary":20,"needNum":2,"description":"维护自习室秩序"}'

# 2. 助学中心审核通过（把上一步返回的 jobId 填进去）
curl -X POST http://localhost:8082/api/job/auditJob \
  -H "Content-Type: application/json" -H "token: <center的token>" \
  -d '{"jobId":7,"auditResult":"pass","auditRemark":"同意上架"}'

# 3. 学生浏览岗位
curl "http://localhost:8082/api/job/listJob?page=1&size=10"

# 4. 学生投递
curl -X POST http://localhost:8082/api/job/applyJob \
  -H "Content-Type: application/json" -H "token: <student1的token>" \
  -d '{"jobId":7,"userId":4}'

# 5. 部门管理员录用
curl -X POST http://localhost:8082/api/job/auditApply \
  -H "Content-Type: application/json" -H "token: <deptA的token>" \
  -d '{"applyId":5,"applyResult":"accept","remark":"同意录用"}'

# 6. 工时服务校验录用资格
curl "http://localhost:8082/api/job/getStudentHired?jobId=7&userId=4"

# 7. 部门管理员下架岗位（jobId 走 JSON 请求体）
curl -X POST http://localhost:8082/api/job/offlineJob \
  -H "Content-Type: application/json" -H "token: <deptA的token>" \
  -d '{"jobId":7}'

# 8. 查询岗位详情（已下架/已招满岗位同样可查，供工时服务核算补贴）
curl "http://localhost:8082/api/job/getJob?jobId=5"
```

## 五、状态机与业务规则

### 5.1 岗位状态

```
草稿(0) → 待中心审核(1) → 已上架(2) / 已驳回(3) → 已招满(4) → 已下架(5)
```

- 发布即进入 **待中心审核**，草稿状态仅保留在字典中；
- 只有 **待中心审核** 的岗位能被审核，重复审核返回 400；
- 驳回必须填写 `auditRemark`，被驳回岗位修改后自动回到 **待中心审核**；
- 只有 **已上架** 岗位能被浏览、投递、录用；
- 录用人数达到 `needNum` 时自动转为 **已招满**，同时不能继续录用；
- 只有 **已上架 / 已招满** 的岗位能下架。

### 5.2 申请状态

```
待审核(0) → 已录用(1) / 已拒绝(2)
```

- `(job_id, user_id)` 数据库唯一约束 + 业务查重，双重保证"同一岗位只能投递一次"；
- 已处理过的申请不能重复审核；
- `getStudentHired` 只在状态为 **已录用** 时返回 `true`（没投递、被拒绝都返回 `false`）。

### 5.3 业务冲突与异常处理（对应成员 4 的"处理业务逻辑冲突、完善异常处理"）

| 场景 | 返回 |
|---|---|
| 学生、中心管理员调用发布岗位 | 403 |
| 部门管理员操作其他部门岗位 | 403 |
| 学生冒用他人 userId 投递 / 查询 | 403 |
| 重复投递同一岗位 | 400「已投递过该岗位」 |
| 投递未上架（待审核/驳回/下架/招满）岗位 | 400 |
| 岗位名额已满仍录用 | 400 |
| 两个管理员同时录用最后一个名额（乐观锁冲突） | 400「该岗位刚被其他人更新，请刷新后重试」，本次操作整体回滚 |
| 两个管理员同时审核同一份申请（一个录用一个拒绝，乐观锁冲突） | 400，后提交的一方整体回滚，不会出现"人数已加但申请被改成拒绝" |
| 修改岗位时把名称/地点/描述改成空字符串 | 400「…不能为空」 |
| 需求人数改得小于已录用人数 | 400 |
| 已下架/已招满岗位修改 | 400 |
| 驳回不填理由 | 400 |
| Token 缺失/失效 | 401 |
| 权限服务未启动、网络不通 | 500「权限服务暂不可用，请稍后重试」 |
| 参数缺失、类型错误、JSON 格式错误、数据库唯一约束冲突 | 400 |
| 其他未预期异常 | 500（堆栈只进日志，不返回前端） |

## 六、与权限服务、工时服务的对接

```
前端 / 其他服务
      │  请求头 token
      ▼
job-service(8082) ──GET /api/auth/checkToken────▶ auth-service(8081)
      │                 GET /api/auth/getUserInfo（取 deptId 做部门隔离）
      │
      ▼  工时服务签到链路
attendance-service(8083)
   1. GET /api/auth/checkToken?token=xx         → 401 则拒绝
   2. GET /api/job/getStudentHired?jobId&userId → false 则拒绝
   3. GET /api/job/getJob?jobId=xx              → 取 hourSalary、deptId 核算补贴
   4. 以上都通过，才允许新增签到记录
```

> **补贴计算注意**：`getJob` 返回的是**当前时薪**。部门管理员修改时薪后，历史月份的补贴不应被改写，
> 建议工时服务在签到时（或月结时）把 `hourSalary` 快照存进自己的工时记录，历史结算使用快照值。

- 本服务**不保存用户表**，只存 `user_id`、`dept_id` 这类逻辑外键（分布式独立数据库，不能跨库外键）；
- `application.yml` 里的 `auth.service.base-url` 跨机器联调时改成对方局域网 IP；
- 权限服务返回结构解析集中在 `client/AuthClient.java`，对方接口若调整只需改这一处。

## 七、预置演示数据（data.sql，幂等）

| 岗位 id | 名称 | 部门 | 状态 | 说明 |
|---|---|---|---|---|
| 1 | 图书馆图书整理助理 | 1 | 已上架 | 已录用 1 人（student1） |
| 2 | 计算机学院机房维护助理 | 2 | 已上架 | 可投递 |
| 3 | 迎新志愿服务岗 | 1 | 待中心审核 | 用于演示审核 |
| 4 | 实验室耗材管理员 | 2 | 已驳回 | 修改后重新送审 |
| 5 | 校园快递代取 | 1 | 已下架 | 不可投递 |
| 6 | 会务服务助理 | 1 | 已招满 | 演示"名额已满不能再录用" |

| 申请 id | 岗位 | 学生 userId | 状态 |
|---|---|---|---|
| 1 | 1 | 4（student1，张三） | 已录用 |
| 2 | 1 | 5（student2，李四） | 待审核 |
| 3 | 2 | 4 | 已拒绝 |
| 4 | 6 | 5 | 待审核（岗位已满，录用会失败） |

> 部门 2 的岗位 `publisher_id=99` 为占位：权限服务目前只预置了部门 1 的管理员 deptA。
> 演示"本部门隔离"时，可用 deptA 的 Token 访问部门 2 的岗位，会得到 403。

## 八、项目结构

```
job-service
├── pom.xml
├── README.md
└── src
    ├── main
    │   ├── java/com/campus/job
    │   │   ├── JobServiceApplication.java       # 启动类（8082）
    │   │   ├── controller/JobController.java    # 11 个接口入口
    │   │   ├── service/JobService.java          # 发布/审核/浏览/详情/修改/下架
    │   │   ├── service/ApplyService.java        # 投递/审核申请/录用查询/申请列表
    │   │   ├── service/AuthGuard.java           # Token 校验 + 角色/部门权限拦截
    │   │   ├── client/AuthClient.java           # 调用权限服务的 HTTP 客户端（含超时、异常包装）
    │   │   ├── entity/Job.java, JobApply.java   # 岗位、申请实体
    │   │   ├── repository/…                     # JPA 数据访问
    │   │   ├── dto/…                            # 请求/响应对象
    │   │   ├── common/…                         # 统一返回、异常处理、状态字典、角色常量、CORS
    │   │   └── config/RestTemplateConfig.java   # HTTP 客户端超时配置
    │   └── resources
    │       ├── application.yml                  # 端口、H2、权限服务地址
    │       ├── application-mysql.yml            # MySQL 切换配置
    │       ├── schema.sql                       # t_job、t_job_apply
    │       └── data.sql                         # 演示岗位与申请
    └── test/java/com/campus/job
        └── JobServiceTests.java                 # 33 个单元测试（业务冲突 + 越权 + 参数校验 + 乐观锁）
```

## 九、技术说明

- **跨服务鉴权**：本服务不解析 JWT，统一调权限服务 `checkToken`；
  这样 Token 逻辑（过期、注销、续期）只在一处维护，符合单一职责和服务解耦；
- **权限服务客户端超时**：连接 3s / 读取 5s，避免对方服务卡死拖垮本服务；
- **事务与名额控制**：录用操作在 `@Transactional` 事务内完成"校验名额 → 申请置为已录用 → 岗位人数 +1 → 满额转招满"，
  避免出现"申请已录用但岗位未计数"的中间状态；
- **乐观锁防并发覆盖**：`t_job.version` 与 `t_job_apply.version` + JPA `@Version`。
  两个管理员同时录用最后一个名额时，后提交的一方影响行数为 0；
  同一份申请被同时"录用/拒绝"时，后提交的一方同样失败；
  两种情况都会整体回滚并返回 400「该岗位刚被其他人更新，请刷新后重试」，不会产生人数与状态不一致的数据；
- **唯一约束兜底**：并发重复投递时由数据库 `(job_id, user_id)` 唯一约束拦截，全局异常处理器转成 400；
- **数据库可切换**：默认 H2，启动加 `--spring.profiles.active=mysql` 即切 MySQL。
  注意 `schema.sql` 为通用语法，但 `data.sql` 是 H2 的 `MERGE` 幂等写法，
  MySQL 模式需先改写为 `INSERT ... ON DUPLICATE KEY UPDATE`（见 `application-mysql.yml` 注释）；
- **测试隔离**：单元测试用内存库 + `@Transactional` 回滚，不污染开发用的 `./data/jobdb`。
