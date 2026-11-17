-- 预置数据（幂等：MERGE 已存在则更新，不会重复插入）
-- 演示账号统一密码：123456（数据库中保存的是 BCrypt 加盐哈希，非明文）

-- 角色字典：1 学生 / 2 部门管理员 / 3 助学中心管理员 / 4 系统管理员
MERGE INTO t_role KEY (id) VALUES (1, '学生');
MERGE INTO t_role KEY (id) VALUES (2, '部门管理员');
MERGE INTO t_role KEY (id) VALUES (3, '助学中心管理员');
MERGE INTO t_role KEY (id) VALUES (4, '系统管理员');

-- 演示账号
MERGE INTO t_user (id, username, password, name, student_no, dept_id, role_id) KEY (id)
VALUES (1, 'admin', '$2a$10$KogZaQ1.D3xivQJGs0NmMOCQ09YPEFdEcZ7I/QJSB5M8kDlUUa.M2', '系统管理员', NULL, 0, 4);
MERGE INTO t_user (id, username, password, name, student_no, dept_id, role_id) KEY (id)
VALUES (2, 'deptA', '$2a$10$nQls7TfWu6Rk1zjimFAhde0JIThKMcSkr3.d8zHSO41IbYYCYigAK', '图书馆管理员', NULL, 1, 2);
MERGE INTO t_user (id, username, password, name, student_no, dept_id, role_id) KEY (id)
VALUES (3, 'center', '$2a$10$NMsPr6bAUfoP30D3ORDT0ezj74ctJ8lBhT5GZPWZQ87Xno0s2zow.', '助学中心管理员', NULL, 0, 3);
MERGE INTO t_user (id, username, password, name, student_no, dept_id, role_id) KEY (id)
VALUES (4, 'student1', '$2a$10$c6eaMsqGwqoKP4SaUpU/QeoEMqJkiRem7HeD7KCFDzvjiuoZAbmxa', '张三', '2023123401', 0, 1);
MERGE INTO t_user (id, username, password, name, student_no, dept_id, role_id) KEY (id)
VALUES (5, 'student2', '$2a$10$sPG3ZH2/vJUptYQ3dKUmY.4fX9Vu2PSTBNKSooUvhNClbBlbP4y02', '李四', '2023123402', 0, 1);
