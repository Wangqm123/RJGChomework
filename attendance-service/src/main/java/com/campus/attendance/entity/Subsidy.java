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
 * 月度补贴发放单：一个学生 + 一个部门 + 一个月份 = 一条发放单。
 *
 * 与"实时核算"（mySubsidy / deptSummary）的区别：
 *   1. 实时核算是查询视图，数据随签到记录变化；
 *   2. 发放单是**留痕凭证**，结算时把人数、工时、金额固化下来，
 *      打款之后即使有人补录工时，已发放的金额也不会变（避免"发过的钱对不上账"）。
 *
 * 设计要点（答辩可讲）：
 * - UNIQUE(user_id, dept_id, settle_month)：一人一月一部门只能有一张单，重复结算只能刷新不能新增，
 *   并发结算时由数据库兜底拒绝第二条插入（与签到 active_key 用的是同一套思路）；
 *   按部门拆单是为了让"部门汇总 = 该部门发放单合计"，一个人跨部门打工时各部门各自结算；
 * - record_ids 记录本次结算包含哪些签到记录，出问题可以按 id 回溯到原始打卡；
 * - 金额一律 BigDecimal，工时存分钟，与 t_attendance_record 的口径完全一致。
 */
@Entity
@Table(name = "t_subsidy")
public class Subsidy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 部门快照：结算时刻该学生所属用工部门，用于部门维度对账 */
    @Column(name = "dept_id", nullable = false)
    private Long deptId;

    /** 结算月份 yyyy-MM（列名不用 month，避免与 SQL 的 MONTH() 函数混淆） */
    @Column(name = "settle_month", nullable = false, length = 7)
    private String settleMonth;

    /** 本次结算包含的签到记录条数 */
    @Column(name = "record_count", nullable = false)
    private Integer recordCount;

    /** 本次结算的总工时（分钟），存分钟而不是小时，避免小数精度问题 */
    @Column(name = "work_minutes", nullable = false)
    private Integer workMinutes;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    /** 0 待发放 / 1 已发放（见 SubsidyStatus） */
    @Column(name = "status", nullable = false)
    private Integer status;

    /** 本次结算包含的签到记录 id，逗号分隔，用于对账回溯 */
    @Column(name = "record_ids", length = 2000)
    private String recordIds;

    /** 最近一次结算时间（待发放状态被刷新时会更新） */
    @Column(name = "settle_time")
    private LocalDateTime settleTime;

    /** 打款时间，仅已发放有值 */
    @Column(name = "pay_time")
    private LocalDateTime payTime;

    /** 最后一次操作人（结算人或打款人）的 userId，便于审计 */
    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "remark", length = 200)
    private String remark;

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

    public String getSettleMonth() {
        return settleMonth;
    }

    public void setSettleMonth(String settleMonth) {
        this.settleMonth = settleMonth;
    }

    public Integer getRecordCount() {
        return recordCount;
    }

    public void setRecordCount(Integer recordCount) {
        this.recordCount = recordCount;
    }

    public Integer getWorkMinutes() {
        return workMinutes;
    }

    public void setWorkMinutes(Integer workMinutes) {
        this.workMinutes = workMinutes;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getRecordIds() {
        return recordIds;
    }

    public void setRecordIds(String recordIds) {
        this.recordIds = recordIds;
    }

    public LocalDateTime getSettleTime() {
        return settleTime;
    }

    public void setSettleTime(LocalDateTime settleTime) {
        this.settleTime = settleTime;
    }

    public LocalDateTime getPayTime() {
        return payTime;
    }

    public void setPayTime(LocalDateTime payTime) {
        this.payTime = payTime;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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
