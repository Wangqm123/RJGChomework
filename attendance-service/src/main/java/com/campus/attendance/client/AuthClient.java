package com.campus.attendance.client;

import com.campus.attendance.common.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 权限服务客户端：本服务不自己保存用户、不自己验 Token，
 * 所有身份相关的判断都通过 HTTP 调用权限服务完成（服务解耦，单一职责）。
 *
 * 调用的两个接口（见《接口文档初稿》服务1）：
 * 1. GET /api/auth/checkToken?token=xx   → 校验 Token，拿到 userId、roleId
 * 2. GET /api/auth/getUserInfo?userId=xx → 查询用户姓名、所属部门 deptId（本部门数据隔离 + 汇总展示姓名）
 *
 * 异常策略：
 * - Token 无效 → 401（让前端引导重新登录）
 * - 权限服务不可用 → 500 + 友好提示（不把底层堆栈暴露给前端）
 */
@Component
public class AuthClient {

    private static final Logger log = LoggerFactory.getLogger(AuthClient.class);

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public AuthClient(RestTemplate restTemplate,
                      @Value("${auth.service.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        // 去掉末尾斜杠，避免拼出 http://host:8081//api/... 这种地址
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    /** 校验通过后的会话信息（只取业务需要的字段） */
    public record Session(Long userId, Integer roleId) {
    }

    /** 用户基础信息（只取本服务要用的字段） */
    public record UserInfo(Long userId, String name, Long deptId, Integer roleId) {
    }

    /**
     * 校验 Token 有效性。
     * 权限服务返回 {code:200,data:{valid,userId,roleId,role}} 或 {code:401,...}。
     */
    public Session checkToken(String token) {
        if (token == null || token.isBlank()) {
            throw new BusinessException(401, "未登录或 Token 已失效");
        }
        JsonNode body = get("/api/auth/checkToken?token={token}", token);
        int code = body.path("code").asInt();
        if (code == 401) {
            throw new BusinessException(401, "未登录或 Token 已失效");
        }
        if (code != 200) {
            throw new BusinessException(500, "身份校验失败：" + body.path("msg").asText("权限服务返回异常"));
        }
        JsonNode data = body.path("data");
        if (!data.path("valid").asBoolean(false)) {
            throw new BusinessException(401, "未登录或 Token 已失效");
        }
        return new Session(data.path("userId").asLong(), data.path("roleId").asInt());
    }

    /**
     * 查询用户信息（取 deptId 做部门隔离、取 name 展示）。
     * 用户不存在等情况统一报 500：属于系统内部数据不一致，不该让前端自行处理。
     */
    public UserInfo getUserInfo(Long userId) {
        JsonNode body = get("/api/auth/getUserInfo?userId={userId}", userId);
        int code = body.path("code").asInt();
        if (code != 200) {
            throw new BusinessException(500, "用户信息查询失败（userId=" + userId + "）："
                    + body.path("msg").asText("权限服务返回异常"));
        }
        JsonNode data = body.path("data");
        JsonNode deptNode = data.path("deptId");
        Long deptId = (deptNode.isMissingNode() || deptNode.isNull()) ? 0L : deptNode.asLong();
        return new UserInfo(
                data.path("userId").asLong(),
                data.path("name").asText(""),
                deptId,
                data.path("roleId").asInt());
    }

    /** 统一的 GET 调用与网络异常包装 */
    private JsonNode get(String pathAndQuery, Object... uriVars) {
        String url = baseUrl + pathAndQuery;
        try {
            JsonNode body = restTemplate.getForObject(url, JsonNode.class, uriVars);
            if (body == null) {
                throw new BusinessException(500, "权限服务返回内容为空");
            }
            return body;
        } catch (RestClientException e) {
            log.error("调用权限服务失败：{}", url, e);
            throw new BusinessException(500, "权限服务暂不可用（未启动或网络不通），请稍后重试");
        }
    }
}
