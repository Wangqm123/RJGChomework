-- 建表脚本（H2 兼容 MySQL 语法；切换 MySQL 时此文件可直接使用）
-- 注意：data.sql 使用 H2 的 MERGE 语法，切换 MySQL 时需改写为 INSERT ... ON DUPLICATE KEY UPDATE

-- 签到记录表：一条记录代表"一次签到到签退"
CREATE TABLE IF NOT EXISTS t_attendance_record (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    -- job_id / user_id 是岗位服务、权限服务的逻辑外键：
    -- 分布式服务独立建库，不能建跨库物理外键，也不复制对方的业务表
    job_id         BIGINT        NOT NULL,
    user_id        BIGINT        NOT NULL,
    dept_id        BIGINT        NOT NULL,
    -- 下面三列是"签到时刻的快照"：岗位名、时薪、所属部门
    -- 为什么存快照：① 分布式库不能跨库 join；② 时薪日后会调整，历史工资必须按当时时薪结算
    job_name       VARCHAR(100)  NOT NULL,
    hour_salary    DECIMAL(10,2) NOT NULL,
    check_in_time  TIMESTAMP     NOT NULL,
    check_out_time TIMESTAMP,
    work_minutes   INT,
    -- 0 已签到未签退 / 1 已签退 / 2 异常待确认（如单次工时超 12 小时）
    status         INT           NOT NULL,
    remark         VARCHAR(200),
    -- 并发签到排他键：未签退 = "userId:jobId"，签退后置 NULL（多个 NULL 不参与唯一性判断）
    active_key     VARCHAR(64),
    create_time    TIMESTAMP,
    update_time    TIMESTAMP,
    -- 并发签到的最终防线：同一学生同一岗位存在未签退记录时，active_key 相同，第二条插入必被数据库拒绝。
    -- 业务层先查重只是"友好提示"，真正防重靠这个唯一约束（单实例/多实例都有效，不依赖进程内锁）。
    -- 写在建表语句内而不是单独的 CREATE INDEX，是为了让 MySQL 与 H2 都能直接执行。
    CONSTRAINT uk_att_active UNIQUE (active_key)
);

-- 以下索引用于"我的记录 / 按月汇总 / 定位未签退记录"的查询加速。
-- ⚠ 仅 H2 支持 CREATE INDEX IF NOT EXISTS；切换 MySQL 时请注释掉这三行，
--   首次建库后手工执行等价的 ALTER TABLE ... ADD INDEX（重复执行不会自动跳过）。
CREATE INDEX IF NOT EXISTS idx_att_user_time ON t_attendance_record (user_id, check_in_time);
CREATE INDEX IF NOT EXISTS idx_att_dept_time ON t_attendance_record (dept_id, check_in_time);
CREATE INDEX IF NOT EXISTS idx_att_status    ON t_attendance_record (status);

-- ============================================================
-- 月度补贴发放单：结算（核算留痕）与发放（打款）的凭证表
-- ============================================================
-- 为什么单独建表而不是每次实时算：
--   实时算出的金额会随签到记录变化，补录一条工时就会让"已经打过的钱"对不上账。
--   结算时把人数、工时、金额、包含哪些记录固化下来，之后打款和对账都以这张单为准。
CREATE TABLE IF NOT EXISTS t_subsidy (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT        NOT NULL,
    -- 按部门拆单：一个人跨部门打工时各部门各自一张单，
    -- 这样"部门汇总金额 = 该部门发放单合计"，对账不会串部门
    dept_id       BIGINT        NOT NULL,
    -- 结算月份 yyyy-MM（列名不用 month，避免与 SQL 的 MONTH() 函数混淆）
    settle_month  VARCHAR(7)    NOT NULL,
    record_count  INT           NOT NULL,
    -- 工时存分钟，与 t_attendance_record 口径一致，避免小时小数累计误差
    work_minutes  INT           NOT NULL,
    total_amount  DECIMAL(10,2) NOT NULL,
    -- 0 待发放 / 1 已发放
    status        INT           NOT NULL,
    -- 本次结算包含的签到记录 id，逗号分隔，用于从发放单回溯到原始打卡
    record_ids    VARCHAR(2000),
    settle_time   TIMESTAMP,
    pay_time      TIMESTAMP,
    operator_id   BIGINT,
    remark        VARCHAR(200),
    create_time   TIMESTAMP,
    update_time   TIMESTAMP,
    -- 一人一月一部门只能有一张单：重复结算是"刷新"而不是"新增"，
    -- 并发重复结算时数据库直接拒绝第二条插入，不依赖进程内锁（多实例也有效）
    CONSTRAINT uk_subsidy_user_dept_month UNIQUE (user_id, dept_id, settle_month)
);

-- 按月 + 部门对账、按状态找待发放单时用（⚠ MySQL 需去掉 IF NOT EXISTS，见文件末尾说明）
CREATE INDEX IF NOT EXISTS idx_subsidy_month_dept ON t_subsidy (settle_month, dept_id);
CREATE INDEX IF NOT EXISTS idx_subsidy_month_status ON t_subsidy (settle_month, status);
