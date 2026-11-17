package com.campus.auth;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户权限服务单元测试：
 * 覆盖登录、Token 校验、用户查询、权限拦截四条主线。
 * （分工要求：成员 3 编写该模块单元测试代码）
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthServiceTests {

    @Autowired
    private MockMvc mockMvc;

    /** 测试 1：正确账号密码登录成功，返回 token 和角色 */
    @Test
    void loginSuccess() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student1\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.userId").value(4))
                .andExpect(jsonPath("$.data.role").value("学生"));
    }

    /** 测试 2：密码错误返回 400 */
    @Test
    void loginWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student1\",\"password\":\"wrong\"}"))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg").value("账号或密码错误"));
    }

    /** 测试 3：登录后用拿到的 token 校验身份（模拟岗位/工时服务的调用） */
    @Test
    void checkTokenAfterLogin() throws Exception {
        // 先登录拿 token
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student1\",\"password\":\"123456\"}"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String token = com.jayway.jsonpath.JsonPath.read(body, "$.data.token");

        // 再校验 token
        mockMvc.perform(get("/api/auth/checkToken").param("token", token))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.userId").value(4))
                .andExpect(jsonPath("$.data.role").value("学生"));
    }

    /** 测试 4：无效 token 返回 401 */
    @Test
    void checkInvalidToken() throws Exception {
        mockMvc.perform(get("/api/auth/checkToken").param("token", "bad-token"))
                .andExpect(jsonPath("$.code").value(401));
    }

    /** 测试 4b：JWT 内容被篡改（伪造角色）→ 验签失败返回 401 */
    @Test
    void checkTamperedToken() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student1\",\"password\":\"123456\"}"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String token = com.jayway.jsonpath.JsonPath.read(body, "$.data.token");

        // JWT 三段式：header.payload.signature；把 payload 部分换成乱码，签名必然对不上
        String[] parts = token.split("\\.");
        String tampered = parts[0] + ".eyJ1c2VySWQiOjEsInJvbGVJZCI6NH0." + parts[2];

        mockMvc.perform(get("/api/auth/checkToken").param("token", tampered))
                .andExpect(jsonPath("$.code").value(401));
    }

    /** 测试 5：普通学生调 addUser 被拦截（403 权限不足） */
    @Test
    void addUserDeniedForStudent() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student1\",\"password\":\"123456\"}"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String token = com.jayway.jsonpath.JsonPath.read(body, "$.data.token");

        mockMvc.perform(post("/api/auth/addUser")
                        .header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"newstu\",\"password\":\"123456\",\"name\":\"王五\",\"roleId\":1}"))
                .andExpect(jsonPath("$.code").value(403));
    }

    /** 测试 6：系统管理员新增用户成功 */
    @Test
    void addUserByAdmin() throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"123456\"}"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String token = com.jayway.jsonpath.JsonPath.read(body, "$.data.token");

        // 每次用随机账号名，保证测试可重复执行（数据库中不会撞已有的账号）
        String uniqueName = "stu" + System.currentTimeMillis();
        mockMvc.perform(post("/api/auth/addUser")
                        .header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + uniqueName + "\",\"password\":\"123456\",\"name\":\"赵六\",\"studentNo\":\"2023123999\",\"roleId\":1}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userId").isNumber());
    }
}
