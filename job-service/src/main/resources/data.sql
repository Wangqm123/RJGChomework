-- 预置演示数据（幂等：MERGE 已存在则更新，不会重复插入）
--
-- 岗位状态：0 草稿 / 1 待中心审核 / 2 已上架 / 3 已驳回 / 4 已招满 / 5 已下架
-- 申请状态：0 待审核 / 1 已录用 / 2 已拒绝
-- 用户 id 与权限服务预置账号对应：2=deptA(部门管理员,dept=1) / 3=center / 4=student1 / 5=student2
-- 部门 2 的岗位 publisher_id 用 99 占位（权限服务暂未预置该部门的管理员账号）

-- 岗位：1、2 已上架；3 待审核；4 被驳回；5 已下架；6 已招满
MERGE INTO t_job (id, job_name, address, hour_salary, need_num, hired_num, description, dept_id, status, audit_remark, publisher_id, create_time, update_time) KEY (id)
VALUES (1, '图书馆图书整理助理', '图书馆一楼借阅区', 25.00, 2, 1, '负责图书上架、读者引导、阅览区秩序维护，每周不少于 6 小时', 1, 2, '岗位内容清晰，同意上架', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
MERGE INTO t_job (id, job_name, address, hour_salary, need_num, hired_num, description, dept_id, status, audit_remark, publisher_id, create_time, update_time) KEY (id)
VALUES (2, '计算机学院机房维护助理', '实验楼 B302', 30.00, 1, 0, '协助机房设备巡检、系统重装、上课前环境准备', 2, 2, '同意上架', 99, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
MERGE INTO t_job (id, job_name, address, hour_salary, need_num, hired_num, description, dept_id, status, audit_remark, publisher_id, create_time, update_time) KEY (id)
VALUES (3, '迎新志愿服务岗', '学生活动中心', 20.00, 3, 0, '迎新期间协助新生报到引导、行李搬运', 1, 1, NULL, 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
MERGE INTO t_job (id, job_name, address, hour_salary, need_num, hired_num, description, dept_id, status, audit_remark, publisher_id, create_time, update_time) KEY (id)
VALUES (4, '实验室耗材管理员', '实验楼 A105', 28.00, 1, 0, '耗材登记', 2, 3, '岗位描述过于简单，请补充具体工作内容与时间要求后重新提交', 99, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
MERGE INTO t_job (id, job_name, address, hour_salary, need_num, hired_num, description, dept_id, status, audit_remark, publisher_id, create_time, update_time) KEY (id)
VALUES (5, '校园快递代取', '菜鸟驿站', 18.00, 3, 0, '协助快递分拣与代取点值守', 1, 5, '需求临时取消，已下架', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
MERGE INTO t_job (id, job_name, address, hour_salary, need_num, hired_num, description, dept_id, status, audit_remark, publisher_id, create_time, update_time) KEY (id)
VALUES (6, '会务服务助理', '行政楼报告厅', 22.00, 1, 1, '会议布场、签到、设备调试', 1, 4, '同意上架', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 申请：1 已录用（job1 + student1）；2 待审核（job1 + student2）；3 已拒绝（job2 + student1）；4 待审核（job6 + student2，用于演示"名额已满不能再录用"）
MERGE INTO t_job_apply (id, job_id, user_id, status, remark, apply_time, audit_time) KEY (id)
VALUES (1, 1, 4, 1, '工作认真，录用', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
MERGE INTO t_job_apply (id, job_id, user_id, status, remark, apply_time, audit_time) KEY (id)
VALUES (2, 1, 5, 0, NULL, CURRENT_TIMESTAMP, NULL);
MERGE INTO t_job_apply (id, job_id, user_id, status, remark, apply_time, audit_time) KEY (id)
VALUES (3, 2, 4, 2, '岗位名额有限，已录满', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
MERGE INTO t_job_apply (id, job_id, user_id, status, remark, apply_time, audit_time) KEY (id)
VALUES (4, 6, 5, 0, NULL, CURRENT_TIMESTAMP, NULL);
