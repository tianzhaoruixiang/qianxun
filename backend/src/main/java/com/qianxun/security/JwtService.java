package com.qianxun.security;

import com.qianxun.config.QianxunProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_DISPLAY_NAME = "dn";
    public static final String CLAIM_ROLE = "role";

    private final QianxunProperties properties;
    private SecretKey signingKey;

    public JwtService(QianxunProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void initKey() {
        String secret = properties.getAuth().getJwtSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("qianxun.auth.jwt-secret 不能为空");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("qianxun.auth.jwt-secret 长度须至少 32 字节（HS256）");
        }
        signingKey = Keys.hmacShaKeyFor(bytes);
    }

    public String createToken(String userId, String username, String displayName) {
        return createToken(userId, username, displayName, UserRoles.ADMIN);
    }

    public String createToken(String userId, String username, String displayName, String role) {
        long expSec = Math.max(60, properties.getAuth().getJwtExpirationSeconds());
        Date now = new Date();
        Date exp = new Date(now.getTime() + expSec * 1000L);
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_DISPLAY_NAME, displayName != null ? displayName : "")
                .claim(CLAIM_ROLE, UserRoles.normalize(role))
                .issuedAt(now)
                .expiration(exp)
                .signWith(signingKey)
                .compact();
    }

    public Claims parseAndValidate(String compactJwt) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(compactJwt)
                .getPayload();
    }
}
