package com.campus.attendance.client;

import com.campus.attendance.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;

/**
 * 岗位服务客户端：签到前必须确认"这个学生确实被这个岗位录用了"，
 * 录用关系只存在岗位服务的库里，本服务通过 HTTP 查询（不复制对方数据）。
 *
 * 调用的两个接口（见 job-service/README.md 第四节）：
 * 1. GET /api/job/getStudentHired?jobId&userId → 是否被该岗位录用（true/false）
 * 2. GET /api/job/getJob?jobId                → 岗位详情（时薪 hourSalary、所属部门 deptId）
 *
 * 为什么需要 getJob：算补贴必须知道时薪和部门，而 listJob 只返回"已上架"岗位，
 * 岗位招满/下架后学生的历史工时仍然要结算，所以岗位服务专门提供了任意状态可查的 getJob。
 * 本服务在签到时把时薪、部门、岗位名**快照**进签到记录，结算时用快照值，不回头再查。
 */
@Component
public class JobClient {

    private static final Logger log = LoggerFactory.getLogger(JobClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public JobClient(RestTemplate restTemplate,
                     @Value("${job.service.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** 岗位信息（只取本服务要用的字段，也是签到时要快照的内容） */
    public record JobInfo(Long jobId, String jobName, BigDecimal hourSalary, Long deptId, Integer status) {
    }

    /**
     * 学生是否被该岗位录用。只在岗位服务的申请状态为「已录用」时返回 true；
     * 没投递、被拒绝、待审核都返回 false。
     */
    public boolean isStudentHired(Long jobId, Long userId) {
        JsonNode body = get("/api/job/getStudentHired?jobId={jobId}&userId={userId}", jobId, userId);
        int code = body.path("code").asInt();
        if (code != 200) {
            throw new BusinessException(500, "录用资格查询失败：" + body.path("msg").asText("岗位服务返回异常"));
        }
        return body.path("data").asBoolean(false);
    }

    /**
     * 查询岗位详情（任意状态可查）。
     * 岗位不存在时岗位服务返回 400，这里原样透传提示，方便前端定位。
     */
    public JobInfo getJob(Long jobId) {
        JsonNode body = get("/api/job/getJob?jobId={jobId}", jobId);
        int code = body.path("code").asInt();
        if (code == 400) {
            throw new BusinessException(400, body.path("msg").asText("岗位不存在"));
        }
        if (code != 200) {
            throw new BusinessException(500, "岗位信息查询失败：" + body.path("msg").asText("岗位服务返回异常"));
        }
        JsonNode data = body.path("data");
        JsonNode deptNode = data.path("deptId");
        Long deptId = (deptNode.isMissingNode() || deptNode.isNull()) ? 0L : deptNode.asLong();
        return new JobInfo(
                data.path("jobId").asLong(),
                data.path("jobName").asText(""),
                data.path("hourSalary").decimalValue(),
                deptId,
                data.path("status").asInt());
    }

    /** 统一的 GET 调用与网络异常包装 */
    private JsonNode get(String pathAndQuery, Object... uriVars) {
        String url = baseUrl + pathAndQuery;
        try {
            JsonNode body = restTemplate.getForObject(url, JsonNode.class, uriVars);
            if (body == null) {
                throw new BusinessException(500, "岗位服务返回内容为空");
            }
            return body;
        } catch (RestClientException e) {
            log.error("调用岗位服务失败：{}", url, e);
            throw new BusinessException(500, "岗位服务暂不可用（未启动或网络不通），请稍后重试");
        }
    }
}
