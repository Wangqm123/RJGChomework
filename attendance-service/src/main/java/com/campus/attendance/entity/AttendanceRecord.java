package com.campus.attendance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 签到记录实体：一条记录 = 一次签到到签退。
 *
 * 设计要点（答辩可讲）：
 * 1. user_id / job_id / dept_id 都是**逻辑外键**，不建跨库物理外键，也不复制用户表和岗位表；
 * 2. job_name / hour_salary / dept_id 是**签到时刻的快照**：
 *    分布式库不能跨库 join，而且时薪日后会调整，历史工资必须按当时时薪结算；
 * 3. work_minutes 存分钟数而不是小时，避免小数精度问题；金额一律用 BigDecimal。
 */
@Entity
@Table(name = "t_attendance_record")
public class AttendanceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false)
    private Long jobId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 岗位所属部门（签到时刻从岗位服务取回后快照，用于部门汇总，不需要再跨服务查） */
    @Column(name = "dept_id", nullable = false)
    private Long deptId;

    /** 岗位名称快照，汇总列表直接展示，避免逐条调用岗位服务 */
    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    /** 时薪快照（元/小时），补贴按此结算，不受岗位后续调薪影响 */
    @Column(name = "hour_salary", nullable = false, precision = 10, scale = 2)
    private BigDecimal hourSalary;

    @Column(name = "check_in_time", nullable = false)
    private LocalDateTime checkInTime;

    @Column(name = "check_out_time")
    private LocalDateTime checkOutTime;

    /** 本次工时（分钟），签退时回填 */
    @Column(name = "work_minutes")
    private Integer workMinutes;

    /** 0 已签到未签退 / 1 已签退 / 2 异常待确认 / 3 核实不通过（见 AttendanceStatus） */
    @Column(name = "status", nullable = false)
    private Integer status;

    @Column(name = "remark", length = 200)
    private String remark;

    /**
     * 异常核实的操作人（userId）与核实时间。
     * 只有 status=2 的记录被核实（→1 或 →3）时才有值，正常签退的记录为空。
     * 这张表里绝大多数字段是学生自己产生的，只有核实是管理员动作，所以单独留痕，便于追责与答辩说明。
     */
    @Column(name = "confirm_operator_id")
    private Long confirmOperatorId;

    @Column(name = "confirm_time")
    private LocalDateTime confirmTime;

    /**
     * 并发签到的排他键：未签退时 = "userId:jobId"，签退后置 null。
     *
     * 为什么需要它：业务层"先查有没有未签退记录、再插入"存在竞态窗口，
     * 两个并发请求会各查一次都查不到，然后各插一条，工时就被重复计算。
     * 数据库层加 UNIQUE(active_key) 兜底后，第二条插入必然被拒；
     * 而签退后置 null，所以同一岗位的历史多次打卡不受影响
     * （多个 NULL 不参与唯一性判断，H2 与 MySQL 行为一致）。
     */
    @Column(name = "active_key", length = 64)
    private String activeKey;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    public void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createTime == null) {
            createTime = now;
        }
        updateTime = now;
    }

    @PreUpdate
    public void preUpdate() {
        updateTime = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getJobId() {
        return jobId;
    }

    public void setJobId(Long jobId) {
        this.jobId = jobId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public BigDecimal getHourSalary() {
        return hourSalary;
    }

    public void setHourSalary(BigDecimal hourSalary) {
        this.hourSalary = hourSalary;
    }

    public LocalDateTime getCheckInTime() {
        return checkInTime;
    }

    public void setCheckInTime(LocalDateTime checkInTime) {
        this.checkInTime = checkInTime;
    }

    public LocalDateTime getCheckOutTime() {
        return checkOutTime;
    }

    public void setCheckOutTime(LocalDateTime checkOutTime) {
        this.checkOutTime = checkOutTime;
    }

    public Integer getWorkMinutes() {
        return workMinutes;
    }

    public void setWorkMinutes(Integer workMinutes) {
        this.workMinutes = workMinutes;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getConfirmOperatorId() {
        return confirmOperatorId;
    }

    public void setConfirmOperatorId(Long confirmOperatorId) {
        this.confirmOperatorId = confirmOperatorId;
    }

    public LocalDateTime getConfirmTime() {
        return confirmTime;
    }

    public void setConfirmTime(LocalDateTime confirmTime) {
        this.confirmTime = confirmTime;
    }

    public String getActiveKey() {
        return activeKey;
    }

    public void setActiveKey(String activeKey) {
        this.activeKey = activeKey;
    }

    /** 未签退记录的排他键：同一学生 + 同一岗位同时只能有一条 */
    public static String buildActiveKey(Long userId, Long jobId) {
        return userId + ":" + jobId;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
