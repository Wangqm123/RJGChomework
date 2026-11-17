package com.campus.job.service;

import com.campus.job.client.AuthClient;
import com.campus.job.common.BusinessException;
import com.campus.job.common.Roles;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 权限拦截组件（对应需求文档 3.1「权限拦截」在本服务侧的落地）。
 *
 * 本服务自己不解析 Token，处理任何需要登录的请求时：
 *   1. 调权限服务 checkToken 拿到 userId / roleId；
 *   2. 角色不匹配直接抛 403；
 *   3. 部门管理员还要再查 deptId，用于"只能操作本部门岗位"的数据隔离。
 */
@Component
public class AuthGuard {

    private final AuthClient authClient;

    public AuthGuard(AuthClient authClient) {
        this.authClient = authClient;
    }

    /** 部门管理员（含 userId、deptId） */
    public record Operator(Long userId, Long deptId) {
    }

    /** 要求当前登录用户是单个指定角色 */
    public AuthClient.Session requireRole(String token, int roleId) {
        return requireAnyRole(token, roleId);
    }

    /** 要求当前登录用户是若干允许角色之一（例如"部门管理员或助学中心管理员"） */
    public AuthClient.Session requireAnyRole(String token, int... allowedRoleIds) {
        AuthClient.Session session = authClient.checkToken(token);
        boolean allowed = Arrays.stream(allowedRoleIds).anyMatch(id -> id == session.roleId());
        if (!allowed) {
            String roles = Arrays.stream(allowedRoleIds)
                    .mapToObj(Roles::nameOf)
                    .collect(Collectors.joining(" / "));
            throw new BusinessException(403, "权限不足：该操作仅" + roles + "可执行");
        }
        return session;
    }

    /** 要求当前登录用户是部门管理员，并返回其所属部门（用于本部门数据隔离） */
    public Operator requireDeptAdmin(String token) {
        return requireDeptAdmin(requireRole(token, Roles.DEPT_ADMIN));
    }

    /**
     * 已校验过角色的场景下直接查部门，避免重复调一次 checkToken。
     * 调用方需自行保证 session.roleId() == DEPT_ADMIN。
     */
    public Operator requireDeptAdmin(AuthClient.Session session) {
        AuthClient.UserInfo info = authClient.getUserInfo(session.userId());
        if (info.deptId() == null || info.deptId() == 0L) {
            throw new BusinessException(403, "权限不足：当前账号未绑定部门，无法管理岗位");
        }
        return new Operator(session.userId(), info.deptId());
    }
}
