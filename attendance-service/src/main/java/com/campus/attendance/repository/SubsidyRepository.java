package com.campus.attendance.repository;

import com.campus.attendance.entity.Subsidy;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 月度补贴发放单数据访问。
 *
 * 查询维度只有三种，够用就不加：某人某部门某月（结算时查重）、按月 + 部门（对账）。
 * 状态过滤放在业务层做——发放单量级很小（一人一月一部门一张），不必为每种组合写一个派生查询。
 */
public interface SubsidyRepository extends JpaRepository<Subsidy, Long> {

    /** 结算时判断"这个人这个部门这个月是否已有发放单"——有则刷新、无则新建 */
    Optional<Subsidy> findByUserIdAndDeptIdAndSettleMonth(
            Long userId, Long deptId, String settleMonth);

    /** 按月查全部（全校视角，中心管理员用） */
    List<Subsidy> findBySettleMonthOrderByDeptIdAscUserIdAsc(String settleMonth);

    /** 按月 + 部门查（部门管理员对账用） */
    List<Subsidy> findBySettleMonthAndDeptIdOrderByUserIdAsc(String settleMonth, Long deptId);

    /** 查某人某月的全部发放单（跨部门可能有几张），学生端展示"有没有结算/发放"用 */
    List<Subsidy> findByUserIdAndSettleMonth(Long userId, String settleMonth);

    /**
     * 条件更新：只有仍处于"待发放"的单才会被改成"已发放"。
     *
     * 为什么不用"读实体 → setStatus → save"：两个管理员同时点发放时，
     * 两边都能读到待发放、都以为自己发了一笔，账面就会重复计数。
     * 带 status = 0 条件的 UPDATE 是原子操作，第二次执行影响行数为 0，
     * 调用方按影响行数统计，天然幂等。
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Subsidy s SET s.status = 1, s.payTime = :payTime, s.operatorId = :operatorId, "
            + "s.updateTime = :payTime WHERE s.id = :id AND s.status = 0")
    int markPaid(@Param("id") Long id,
                 @Param("payTime") LocalDateTime payTime,
                 @Param("operatorId") Long operatorId);
}
