package com.campus.job.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 岗位实体（对应数据库表 t_job）。
 *
 * 说明：dept_id 只是权限服务里部门编号的"逻辑外键"，
 * 分布式架构下用户数据在权限服务自己的库里，本库不建物理外键、也不复制用户表。
 */
@Entity
@Table(name = "t_job")
public class Job {

    /** 岗位 id（主键，自增） */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 岗位名称 */
    @Column(nullable = false, length = 100)
    private String jobName;

    /** 工作地点 */
    @Column(nullable = false, length = 200)
    private String address;

    /** 时薪（元/小时），用 BigDecimal 避免浮点精度问题 */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal hourSalary;

    /** 需求人数 */
    @Column(nullable = false)
    private Integer needNum;

    /** 已录用人数（录用 +1，拒绝不变；达到 needNum 时岗位自动转为"已招满"） */
    @Column(nullable = false)
    private Integer hiredNum;

    /** 岗位描述 / 工作要求 */
    @Column(length = 500)
    private String description;

    /** 所属部门 id（部门管理员只能管理本部门岗位） */
    @Column(nullable = false)
    private Long deptId;

    /** 岗位状态，取值见 common/JobStatus */
    @Column(nullable = false)
    private Integer status;

    /** 审核意见（中心审核通过/驳回时填写，驳回必填理由） */
    @Column(length = 200)
    private String auditRemark;

    /** 发布人 userId（从 Token 中解析，便于追溯） */
    @Column(nullable = false)
    private Long publisherId;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 乐观锁版本号：JPA 每次 update 都会自动生成
     * "update t_job set ..., version = version + 1 where id = ? and version = ?"。
     *
     * 作用：防止两个部门管理员同时录用最后一个名额时，
     * 双方都读到"还有名额"并且都录用成功（超额录用）。
     * 后提交的一方影响行数为 0，触发乐观锁异常，由全局异常处理器转成友好提示，事务整体回滚。
     */
    @Version
    @Column(nullable = false)
    private Long version;

    /** 入库前补默认值和时间戳 */
    @PrePersist
    public void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (hiredNum == null) {
            hiredNum = 0;
        }
        createTime = now;
        updateTime = now;
    }

    @PreUpdate
    public void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getJobName() {
        return jobName;
    }

    public void setJobName(String jobName) {
        this.jobName = jobName;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigDecimal getHourSalary() {
        return hourSalary;
    }

    public void setHourSalary(BigDecimal hourSalary) {
        this.hourSalary = hourSalary;
    }

    public Integer getNeedNum() {
        return needNum;
    }

    public void setNeedNum(Integer needNum) {
        this.needNum = needNum;
    }

    public Integer getHiredNum() {
        return hiredNum;
    }

    public void setHiredNum(Integer hiredNum) {
        this.hiredNum = hiredNum;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getAuditRemark() {
        return auditRemark;
    }

    public void setAuditRemark(String auditRemark) {
        this.auditRemark = auditRemark;
    }

    public Long getPublisherId() {
        return publisherId;
    }

    public void setPublisherId(Long publisherId) {
        this.publisherId = publisherId;
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

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }
}
