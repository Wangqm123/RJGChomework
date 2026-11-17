-- 建表脚本（H2 兼容 MySQL 语法；切换 MySQL 时此文件可直接使用）

-- 角色字典表：系统 4 种角色
CREATE TABLE IF NOT EXISTS t_role (
    id        INT         PRIMARY KEY,
    role_name VARCHAR(50) NOT NULL
);

-- 用户表：role_id 关联 t_role
CREATE TABLE IF NOT EXISTS t_user (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL,   -- BCrypt 加盐哈希（60 字符），不存明文
    name       VARCHAR(50)  NOT NULL,
    student_no VARCHAR(30),
    dept_id    BIGINT DEFAULT 0,
    role_id    INT          NOT NULL,
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES t_role(id)
);
