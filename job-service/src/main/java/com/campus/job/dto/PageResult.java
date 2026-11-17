package com.campus.job.dto;

import java.util.List;

/**
 * 分页返回体：data = { list: [...], total: 总数 }
 * （接口文档 /api/job/listJob 约定：返回岗位列表、总数）
 */
public record PageResult<T>(List<T> list, long total) {
}
