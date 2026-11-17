package com.campus.job.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * 岗位申请实体（对应数据库表 t_job_apply）。
 *
 * (job_id, user_id) 建唯一约束：从数据库层面保证
 * "一个学生对同一岗位只能投递一次"（需求文档 3.2 岗位投递）。
 */
@Entity
@Table(name = "t_job_apply",
        uniqueConstraints = @UniqueConstraint(name = "uk_apply_job_user", columnNames = {"job_id", "user_id"}))
public class JobApply {

    /** 申请记录 id（主键，自增） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 岗位 id */
    @Column(name = "job_id", nullable = false)
    private Long jobId;

    /** 投递学生 userId（逻辑外键，用户数据在权限服务库中） */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 申请状态，取值见 common/ApplyStatus */
    @Column(nullable = false)
    private Integer status;

    /** 审核意见（部门管理员录用/拒绝时填写） */
    @Column(length = 200)
    private String remark;

    /** 投递时间 */
    private LocalDateTime applyTime;

    /** 审核时间 */
    private LocalDateTime auditTime;

    /**
     * 乐观锁版本号：防止两个部门管理员同时对同一份申请做出相反决定
     * （一个录用、一个拒绝）时互相覆盖状态。
     * 后提交的一方影响行数为 0 → 抛乐观锁异常 → 整个事务回滚。
     */
    @Version
    @Column(nullable = false)
    private Long version;

    @PrePersist
    public void onCreate() {
        if (applyTime == null) {
            applyTime = LocalDateTime.now();
        }
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

    public LocalDateTime getApplyTime() {
        return applyTime;
    }

    public void setApplyTime(LocalDateTime applyTime) {
        this.applyTime = applyTime;
    }

    public LocalDateTime getAuditTime() {
        return auditTime;
    }

    public void setAuditTime(LocalDateTime auditTime) {
        this.auditTime = auditTime;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
