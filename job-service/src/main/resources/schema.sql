-- 建表脚本（H2 兼容 MySQL 语法；切换 MySQL 时此文件可直接使用）

-- 岗位表
CREATE TABLE IF NOT EXISTS t_job (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_name     VARCHAR(100)  NOT NULL,
    address      VARCHAR(200)  NOT NULL,
    hour_salary  DECIMAL(10,2) NOT NULL,
    need_num     INT           NOT NULL,
    hired_num    INT           NOT NULL DEFAULT 0,
    description  VARCHAR(500),
    -- dept_id / publisher_id 是权限服务中部门、用户的逻辑外键：
    -- 分布式服务独立建库，不能建跨库物理外键，也不复制用户表
    dept_id      BIGINT        NOT NULL,
    status       INT           NOT NULL,
    audit_remark VARCHAR(200),
    publisher_id BIGINT        NOT NULL,
    -- 乐观锁版本号：并发修改同一岗位时由 JPA 自动校验，防止超额录用
    version      BIGINT        NOT NULL DEFAULT 0,
    create_time  TIMESTAMP,
    update_time  TIMESTAMP
);

-- 岗位申请表
CREATE TABLE IF NOT EXISTS t_job_apply (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    job_id     BIGINT      NOT NULL,
    user_id    BIGINT      NOT NULL,
    status     INT         NOT NULL,
    remark     VARCHAR(200),
    apply_time TIMESTAMP,
    audit_time TIMESTAMP,
    -- 乐观锁版本号：防止同一份申请被并发做出相反审核决定
    version    BIGINT      NOT NULL DEFAULT 0,
    -- 一个学生对同一岗位只能投递一次（需求文档 3.2）
    CONSTRAINT uk_apply_job_user UNIQUE (job_id, user_id)
);
