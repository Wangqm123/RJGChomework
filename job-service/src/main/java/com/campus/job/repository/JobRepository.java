package com.campus.job.repository;

import com.campus.job.entity.Job;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 岗位表数据访问层。继承 JpaRepository 即自动获得增删改查能力。
 */
public interface JobRepository extends JpaRepository<Job, Long> {

    /**
     * 按状态分页查询，按 id 倒序（新发布的在前）。
     * 学生浏览岗位只查 status=已上架(2)。
     */
    Page<Job> findByStatusOrderByIdDesc(Integer status, Pageable pageable);
}
