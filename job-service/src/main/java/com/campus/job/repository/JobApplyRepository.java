package com.campus.job.repository;

import com.campus.job.entity.JobApply;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 岗位申请表数据访问层。
 */
public interface JobApplyRepository extends JpaRepository<JobApply, Long> {

    /** 查某个学生对某个岗位的申请（判断是否重复投递、是否被录用） */
    Optional<JobApply> findByJobIdAndUserId(Long jobId, Long userId);

    /** 是否已投递过该岗位（投递前查重） */
    boolean existsByJobIdAndUserId(Long jobId, Long userId);

    /** 我的申请记录（学生视角，新的在前） */
    List<JobApply> findByUserIdOrderByIdDesc(Long userId);

    /** 某个岗位下的全部申请（部门管理员审核用） */
    List<JobApply> findByJobIdOrderByIdAsc(Long jobId);
}
