package com.campus.auth.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Token 服务（JWT 实现）：登录成功后签发 JWT，之后的请求带上 Token 证明身份。
 *
 * 为什么用 JWT（分布式系统的关键设计）：
 * 1. 无状态 —— Token 本身自包含用户信息（userId、roleId、过期时间），
 *    校验时只需验签名，不查数据库、不依赖本机内存表；
 * 2. 服务重启 Token 不失效（旧方案 Token 存内存，重启全部失效）；
 * 3. 天然适配分布式部署 —— 只要约定同一个签名密钥，岗位服务、工时服务
 *    甚至可以本地验签（本系统按接口文档约定仍通过 checkToken 接口集中校验，
 *    保留单一职责，方便日后统一注销/续期）。
 *
 * JWT 结构：header.payload.signature，payload 为 Base64 可读的 JSON，
 * 签名保证内容不可篡改（改一个字节验签即失败）。
 */
@Service
public class TokenService {

    /** Token 携带的会话信息 */
    public record SessionInfo(Long userId, Integer roleId) {
    }

    private final SecretKey signKey;
    private final long ttlMillis;

    public TokenService(@Value("${auth.jwt.secret}") String secret,
                        @Value("${auth.jwt.ttl-hours:24}") long ttlHours) {
        // HMAC-SHA256 要求密钥长度至少 32 字节
        this.signKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = ttlHours * 60 * 60 * 1000L;
    }

    /** 登录成功后签发 JWT：payload 里写入 userId、roleId 和过期时间 */
    public String createToken(Long userId, Integer roleId) {
        Date now = new Date();
        return Jwts.builder()
                .claim("userId", userId)
                .claim("roleId", roleId)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(signKey)
                .compact();
    }

    /**
     * 校验 Token：验签 + 验过期时间。
     * 有效返回用户信息；无效（签名被篡改 / 格式错误 / 已过期）返回 null。
     */
    public SessionInfo validate(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            Long userId = claims.get("userId", Long.class);
            Integer roleId = claims.get("roleId", Integer.class);
            if (userId == null || roleId == null) {
                return null;
            }
            return new SessionInfo(userId, roleId);
        } catch (JwtException | IllegalArgumentException e) {
            // 签名不匹配、Token 过期、格式非法都走这里
            return null;
        }
    }
}
