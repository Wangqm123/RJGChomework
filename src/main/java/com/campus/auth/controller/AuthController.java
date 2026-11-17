package com.campus.auth.controller;

import com.campus.auth.common.ApiResponse;
import com.campus.auth.dto.AddUserRequest;
import com.campus.auth.dto.CheckTokenResponse;
import com.campus.auth.dto.LoginRequest;
import com.campus.auth.dto.LoginResponse;
import com.campus.auth.dto.UserInfoResponse;
import com.campus.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 用户权限服务对外接口（接口文档初稿 · 服务1，前缀 /api/auth）
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * POST /api/auth/login 用户登录
     * 请求参数：username 账号，password 密码
     * 返回 data：token、userId、roleId、role
     */
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req.getUsername(), req.getPassword()));
    }

    /**
     * GET /api/auth/checkToken 校验 Token 有效性（给其他服务调用）
     * Token 支持两种传递方式（二选一）：URL 参数 token，或请求头 token。
     * 返回 data：校验结果、用户 id、角色
     */
    @GetMapping("/checkToken")
    public ApiResponse<CheckTokenResponse> checkToken(
            @RequestParam(value = "token", required = false) String tokenParam,
            @RequestHeader(value = "token", required = false) String tokenHeader) {
        // URL 参数优先，其次请求头（给联调双方都留了方便）
        String token = (tokenParam != null && !tokenParam.isBlank()) ? tokenParam : tokenHeader;
        return ApiResponse.ok(authService.checkToken(token));
    }

    /**
     * GET /api/auth/getUserInfo 根据用户 id 查询用户信息
     * 请求参数：userId
     * 返回 data：用户姓名、学号、部门等
     */
    @GetMapping("/getUserInfo")
    public ApiResponse<UserInfoResponse> getUserInfo(@RequestParam Long userId) {
        return ApiResponse.ok(authService.getUserInfo(userId));
    }

    /**
     * POST /api/auth/addUser 系统管理员新增用户
     * 操作者 Token 放在请求头 token 中（服务端做权限拦截，非管理员返回 403）
     * 请求参数：username、password、name、roleId（studentNo/deptId 选填）
     * 返回 data：新增用户 id
     */
    @PostMapping("/addUser")
    public ApiResponse<Map<String, Long>> addUser(@RequestHeader(value = "token", required = false) String token,
                                                  @Valid @RequestBody AddUserRequest req) {
        Long newUserId = authService.addUser(token, req);
        return ApiResponse.ok(Map.of("userId", newUserId));
    }
}
