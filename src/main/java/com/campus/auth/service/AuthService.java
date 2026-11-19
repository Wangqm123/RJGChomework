package com.campus.auth.service;

import com.campus.auth.common.BusinessException;
import com.campus.auth.common.Roles;
import com.campus.auth.dto.AddUserRequest;
import com.campus.auth.dto.CheckTokenResponse;
import com.campus.auth.dto.LoginResponse;
import com.campus.auth.dto.UserInfoResponse;
import com.campus.auth.entity.User;
import com.campus.auth.repository.RoleRepository;
import com.campus.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 权限服务核心业务逻辑（对应需求文档 3.1 的四条需求）：
 * 1. 用户登录  2. 身份校验  3. 用户管理  4. 权限拦截
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                       TokenService tokenService, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.tokenService = tokenService;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 需求 1：用户登录
     * 校验账号密码，通过则颁发 Token。
     * 密码用 BCrypt 比对（数据库里存的是加盐哈希，不存明文）。
     */
    public LoginResponse login(String username, String password) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(400, "账号或密码错误"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BusinessException(400, "账号或密码错误");
        }

        String token = tokenService.createToken(user.getId(), user.getRoleId());
        return new LoginResponse(token, user.getId(), user.getRoleId(),
                Roles.nameOf(user.getRoleId()));
    }

    /**
     * 需求 2：身份校验（给其他两个服务调用）
     * Token 无效统一返回 401，让调用方知道用户未登录/登录已失效。
     */
    public CheckTokenResponse checkToken(String token) {
        TokenService.SessionInfo info = tokenService.validate(token);
        if (info == null) {
            throw new BusinessException(401, "未登录或 Token 已失效");
        }
        return CheckTokenResponse.of(info.userId(), info.roleId(), Roles.nameOf(info.roleId()));
    }

    /**
     * 需求 3：查询用户信息
     * 联调评审 3-2：本接口返回姓名/学号/部门等个人信息，必须先校验 Token；
     * 业务服务（岗位/工时）调用时需透传用户的原始 Token。
     */
    public UserInfoResponse getUserInfo(String token, Long userId) {
        if (tokenService.validate(token) == null) {
            throw new BusinessException(401, "未登录或 Token 已失效");
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(400, "用户不存在"));
        UserInfoResponse resp = new UserInfoResponse();
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setName(user.getName());
        resp.setStudentNo(user.getStudentNo());
        resp.setDeptId(user.getDeptId());
        resp.setRoleId(user.getRoleId());
        resp.setRole(Roles.nameOf(user.getRoleId()));
        return resp;
    }

    /**
     * 需求 4：用户管理 + 权限拦截
     * 只有系统管理员（roleId=4）能新增用户，其他人调用返回 403。
     */
    public Long addUser(String operatorToken, AddUserRequest req) {
        // 权限拦截：校验操作者身份
        TokenService.SessionInfo operator = tokenService.validate(operatorToken);
        if (operator == null) {
            throw new BusinessException(401, "未登录或 Token 已失效");
        }
        if (operator.roleId() != Roles.SYSTEM_ADMIN) {
            throw new BusinessException(403, "权限不足：仅系统管理员可新增用户");
        }

        // 账号查重
        if (userRepository.existsByUsername(req.getUsername())) {
            throw new BusinessException(400, "账号已存在：" + req.getUsername());
        }

        // 角色合法性校验：roleId 必须存在于角色字典表（1-4）
        if (req.getRoleId() == null || !roleRepository.existsById(req.getRoleId())) {
            throw new BusinessException(400, "角色编号不合法：" + req.getRoleId());
        }

        // 密码强度校验（真实系统的基本要求）
        if (req.getPassword() == null || req.getPassword().length() < 6) {
            throw new BusinessException(400, "密码长度不能少于 6 位");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        // 入库前先 BCrypt 加密，数据库永远不存明文密码
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setName(req.getName());
        user.setStudentNo(req.getStudentNo());
        Long deptId = 0L;
        if (req.getDeptId() != null) {
            deptId = req.getDeptId();
        }
        user.setDeptId(deptId);
        user.setRoleId(req.getRoleId());
        User saved = userRepository.save(user);
        return saved.getId();
    }
}
