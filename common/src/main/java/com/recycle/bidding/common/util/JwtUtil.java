package com.recycle.bidding.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类
 *
 * 用于生成和验证访问令牌。密钥硬编码仅用于演示/开发环境，
 * 生产环境应从配置中心（Nacos）或密钥管理服务获取。
 */
public class JwtUtil {

    /** 密钥：至少 256 位，HMAC-SHA256 要求 */
    private static final SecretKey SECRET_KEY = Keys.hmacShaKeyFor(
            "RecycleBidding@2026!SecretKey#ForJWT-HmacSHA256".getBytes(StandardCharsets.UTF_8)
    );

    /** 令牌有效期：24 小时 */
    private static final long EXPIRATION_MS = 24 * 60 * 60 * 1000L;

    private JwtUtil() {}

    /**
     * 生成 JWT
     *
     * @param userId 用户/商户/工程师 ID
     * @param role   角色：USER / MERCHANT / ENGINEER
     * @return JWT 字符串
     */
    public static String generateToken(Long userId, String role) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("role", role)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + EXPIRATION_MS))
                .signWith(SECRET_KEY)
                .compact();
    }

    /**
     * 验证并解析 JWT
     *
     * @param token JWT 字符串
     * @return Claims（包含 subject=userId, claim=role）
     * @throws JwtException 令牌无效/过期/签名不匹配
     */
    public static Claims parseToken(String token) {
        return Jwts.parser()
                .verifyWith(SECRET_KEY)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 JWT 中提取用户 ID
     */
    public static Long getUserId(String token) {
        return Long.valueOf(parseToken(token).getSubject());
    }

    /**
     * 从 JWT 中提取角色
     */
    public static String getRole(String token) {
        return parseToken(token).get("role", String.class);
    }

    /**
     * 验证令牌是否有效
     */
    public static boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
