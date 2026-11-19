# 校园勤工助学岗位分布式管理系统

课程小组项目总 README：三个独立的 Spring Boot 服务，各自独立进程、独立数据库，只通过 HTTP 接口通信。

## 一、系统总览

| 服务 | 端口 | 代码位置 | 负责人 | 状态 | 文档 |
|---|---|---|---|---|---|
| 用户权限服务（auth-service） | 8081 | 仓库根目录 | 成员 3 | 已完成 | 见本文第五节、《交接说明.md》 |
| 岗位申请与审核服务（job-service） | 8082 | `job-service/` | 成员 4 | 已完成 | [job-service/README.md](job-service/README.md) |
| 工时考勤与补贴核算服务（attendance-service） | 8083 | 待建 | 成员 5 | 开发中 | 接口见《接口文档初稿》服务3 |

> 说明：本仓库根目录本身就是用户权限服务工程（成员 3 建立），
> 岗位服务是与它平级的独立 Maven 工程，放在 `job-service/` 目录下，可单独构建、单独部署。

## 二、全组统一约定（三个服务必须一致）

**统一返回格式**

```json
{ "code": 200, "msg": "操作成功", "data": {} }
```

**错误码**：200 成功 / 400 参数错误（含业务规则冲突）/ 401 未登录或 Token 失效 / 403 权限不足 / 500 服务内部异常。

**角色编号**

| roleId | 角色 |
|---|---|
| 1 | 学生 |
| 2 | 用工部门管理员 |
| 3 | 勤工助学中心管理员 |
| 4 | 系统管理员 |

**Token 约定**：在权限服务登录拿到 JWT（24 小时有效），之后请求把 Token 放在请求头 `token` 中；
业务服务不自己验签，统一调用权限服务 `/api/auth/checkToken` 校验。

## 三、服务间调用链路

```
                    ① 登录拿 Token
   前端 / 演示页面 ──────────────────────▶ auth-service(8081)
        │
        │ ② 业务请求（请求头 token）
        ▼
   job-service(8082) ──GET /api/auth/checkToken────▶ auth-service
        │                 GET /api/auth/getUserInfo（取 deptId 做部门隔离）
        │
        ▼ ③ 工时服务签到链路（需求文档 3.3）
   attendance-service(8083)
     1. GET /api/auth/checkToken?token=xx          → 401 则拒绝
     2. GET /api/job/getStudentHired?jobId&userId  → false 则拒绝
     3. GET /api/job/getJob?jobId=xx               → 取 hourSalary、deptId 核算补贴（需存时薪快照）
     4. 以上都通过，才允许新增签到记录
```

## 四、文档与工具索引

| 文件 | 内容 | 维护人 |
|---|---|---|
| `job-service/README.md` | 岗位服务：接口、状态机、权限矩阵、联调说明 | 成员 4 |
| `交接说明.md` | 权限服务交接：接口约定、接入步骤、排错速查 | 成员 3 |
| `环境搭建指南.md` | JDK / VS Code / Maven 环境配置 | 成员 3 |
| `接口测试台.html` | 权限服务浏览器图形化测试工具 | 成员 3 |
| `原始任务分布.doc`、`需求文档.doc`、`接口文档初稿.doc` | 分工、需求、接口原始材料 | 全体 |

---

## 五、服务一：用户权限服务（auth-service，8081）

### 5.1 这个服务是干什么的

整个系统的"大门"：所有用户先在这里登录拿 Token（JWT），岗位审核服务、工时核算服务都依赖它校验身份。

- **用户登录**：账号密码登录，签发 JWT Token（24 小时有效）
- **身份校验**：提供 `/checkToken` 接口给其他两个服务调用，支持 URL 参数或请求头传 Token
- **用户管理**：系统管理员新增用户、分配角色（密码入库前 BCrypt 加密）
- **权限拦截**：非系统管理员调 addUser 直接返回 403
- **参数校验**：非法参数（角色编号越界、密码过短等）返回 400 并提示具体原因

### 5.2 环境准备（macOS / Windows 通用）

| 工具 | 说明 |
|---|---|
| JDK 17 | 下载 [Temurin JDK 17](https://adoptium.net/)，或 macOS 上 `brew install openjdk@17` |
| VS Code | 安装 **Extension Pack for Java** 扩展（会自动带上 Spring Boot 插件） |
| Maven | 可不装。VS Code Java 插件能直接运行；命令行党装 [Maven](https://maven.apache.org/download.cgi) 即可 |

> Mac 和 Windows 代码完全通用（Java 跨平台），组员各自在自己电脑上跑即可，
> 这正好满足课程"多个子系统跑在不同机器"的要求。
> 详细的安装配置步骤见《环境搭建指南.md》。

### 5.3 怎么跑起来

**方式 1（推荐，VS Code）：**
用 VS Code 打开仓库根目录（即 auth-service 工程）→ 等 Java 插件索引完成 → 打开
`AuthServiceApplication.java` → 点 main 方法上方的「Run」。

**方式 2（命令行）：**

```bash
# 在仓库根目录（auth-service 工程根）执行
mvn spring-boot:run
```

启动成功后服务跑在 **8081 端口**（约定：auth=8081 / job=8082 / attendance=8083）。

**首次使用先跑一遍测试（验证环境没问题）：**
在测试文件 `AuthServiceTests.java` 上点 Run，7 个用例全绿即 OK。

### 5.4 接口一览（按《接口文档初稿》实现）

统一返回格式：`{"code": 200, "msg": "操作成功", "data": {}}`
错误码：200 成功 / 400 参数错误 / 401 未登录或 Token 失效 / 403 权限不足 / 500 内部异常
完整地址 = `http://<运行该服务的机器IP>:8081` + 路径

| 接口 | 方式 | 说明 | 权限 |
|---|---|---|---|
| `/api/auth/login` | POST | 登录，返回 token、userId、roleId、role | 所有人 |
| `/api/auth/checkToken` | GET | 校验 Token（`?token=xx` 或请求头 `token: xx`），返回 valid、userId、role | 其他服务内部调用 |
| `/api/auth/getUserInfo` | GET | 查用户姓名、学号、部门、角色（Header 或 URL 参数带 token，缺失/无效返回 401） | 需登录（联调评审 3-2 起） |
| `/api/auth/addUser` | POST | 新增用户、分配角色（Header 带 token） | 仅系统管理员 |

**角色编号约定（全组统一）：** 1 学生 / 2 用工部门管理员 / 3 助学中心管理员 / 4 系统管理员

### 5.5 接口测试

三种方式任选：

1. **接口控制台**：双击打开《接口测试台.html》，状态灯变绿后点按钮即可测全部接口，含一键全流程演示
2. **单元测试**：`mvn test` 或在 `AuthServiceTests.java` 点 Run Tests（7 个用例，含越权 403、篡改 Token 401 反例）
3. **curl**（假设服务在本机运行）：

```bash
# 1. 学生登录
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"student1","password":"123456"}'
# 返回 data.token 记下来

# 2. 校验 Token（岗位服务/工时服务就是这么调的）
curl "http://localhost:8081/api/auth/checkToken?token=刚才拿到的token"

# 3. 查用户信息（联调评审 3-2 起需要带 token）
curl "http://localhost:8081/api/auth/getUserInfo?userId=4&token=用户登录拿到的token"

# 4. 系统管理员新增一个学生账号
curl -X POST http://localhost:8081/api/auth/addUser \
  -H "Content-Type: application/json" \
  -H "token: 管理员登录拿到的token" \
  -d '{"username":"studentX","password":"123456","name":"王五","studentNo":"2023123001","roleId":1}'
```

### 5.6 预置演示账号（data.sql 自动初始化，密码统一 123456）

| 账号 | 角色 |
|---|---|
| admin | 系统管理员 |
| deptA | 用工部门管理员（图书馆） |
| center | 助学中心管理员 |
| student1 | 学生（张三） |
| student2 | 学生（李四） |

数据库里存的是 BCrypt 加盐哈希，不存明文。

### 5.7 其他服务怎么接入（联调要点）

其他服务收到前端请求后，先调 checkToken 校验身份，再执行业务：

```
工时服务签到流程（需求文档约定）：
1. 调 GET /api/auth/checkToken?token=xx     → 401 则拒绝，200 拿到 userId
2. 调 GET /api/job/getStudentHired?jobId&userId → 判断是否被录用
3. 两关都过，才允许新增签到记录
```

岗位服务的实际接入方式（含超时、异常包装、部门隔离）见 [job-service/README.md](job-service/README.md) 第六节。
详细的接口约定、代码对齐建议、排错速查见《交接说明.md》。

### 5.8 技术说明

- **JWT Token（无状态）**：登录签发 JWT（HMAC-SHA256 签名，24 小时过期），校验只验签名不查库、不依赖内存，服务重启 Token 也不失效——分布式系统的标准做法。密钥和有效期在 `application.yml` 的 `auth.jwt` 配置
- **BCrypt 密码加密**：数据库只存加盐哈希（`$2a$10$...`），登录用 `PasswordEncoder.matches` 比对，新增用户入库前自动加密——同一密码每次哈希结果都不同，库泄露也反查不出明文
- **角色字典表**：`t_role` 表管理 4 种角色，新增用户时先校验 roleId 合法性（外键约束 + 业务双重校验）
- **数据库可切换**：默认 H2（零安装）；启动时加 `--spring.profiles.active=mysql` 即切 MySQL，配置在 `application-mysql.yml`
- **参数校验**：DTO 上有 @NotNull/@Max 等注解，非法参数返回 400 并提示具体字段
- **分布式体现**：本服务(8081)、岗位服务(8082)、工时服务(8083) 独立进程、独立数据库，
  只通过 HTTP 接口通信 —— 这就是「高内聚低耦合 + 服务解耦」

### 5.9 项目结构

```
仓库根目录（auth-service 工程）
├── pom.xml                          # 权限服务 Maven 依赖（Spring Boot 3.3 + Java 17 + JJWT + BCrypt）
├── README.md                        # 本文件（系统总 README，含权限服务详细说明）
├── 交接说明.md                       # 接口约定、接入步骤、排错（给后续成员）
├── 环境搭建指南.md                   # 环境配置步骤（macOS / Windows）
├── 接口测试台.html                   # 浏览器图形化测试工具
├── job-service/                     # 岗位申请与审核服务（成员 4，独立 Maven 工程，端口 8082）
└── src
    ├── main
    │   ├── java/com/campus/auth
    │   │   ├── AuthServiceApplication.java   # 启动类
    │   │   ├── controller/AuthController.java # 四个接口定义
    │   │   ├── service/AuthService.java       # 业务逻辑（登录/校验/管理/拦截）
    │   │   ├── service/TokenService.java      # JWT 颁发与验签
    │   │   ├── entity/User.java, Role.java    # 用户实体、角色字典实体
    │   │   ├── repository/UserRepository.java, RoleRepository.java
    │   │   ├── dto/…                          # 请求/响应对象
    │   │   └── common/…                       # 统一返回、全局异常、角色常量、加密配置
    │   └── resources
    │       ├── application.yml                # 默认配置（H2 + JWT 密钥）
    │       ├── application-mysql.yml          # MySQL 切换配置
    │       ├── schema.sql                     # 建表语句（t_role + t_user）
    │       └── data.sql                       # 角色字典 + 预置演示账号（哈希密码）
    └── test/java/com/campus/auth
        └── AuthServiceTests.java              # 7 个单元测试用例
```

## 六、服务二：岗位申请与审核服务（job-service，8082）

概要（详细文档见 [job-service/README.md](job-service/README.md)）：

- **岗位发布**（部门管理员）→ **中心审核**（上架 / 驳回）→ **学生投递** → **部门管理员录用/拒绝** → **招满 / 下架**
- 7 个接口严格按《接口文档初稿》服务2 实现，另补充修改、下架、申请列表、岗位详情 4 个接口（`getJob` 供工时服务核算补贴）
- 身份校验调用权限服务 `checkToken`，并调 `getUserInfo` 取 deptId 做本部门数据隔离
- 对工时服务提供 `GET /api/job/getStudentHired`（录用判断）和 `GET /api/job/getJob`（时薪/部门）
- 单元测试 33 个（业务冲突 + 越权 + 参数校验 + 乐观锁），`cd job-service && mvn test`

## 七、服务三：工时考勤与补贴核算服务（attendance-service，8083）

由成员 5 负责开发，接口见《接口文档初稿》服务3（签到、签退、月度工时补贴、部门/全校汇总），
本 README 暂不展开，完成后由成员 5 补充说明。
