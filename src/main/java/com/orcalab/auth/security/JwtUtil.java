package com.orcalab.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;

/**
 * Genera y valida JWT firmados con HMAC-SHA256.
 *
 * IMPORTANTE para la arquitectura: la MISMA clave secreta (orcalab.jwt.secret)
 * debe configurarse en los 5 microservicios para que cada uno pueda validar
 * el token localmente sin llamar siempre a auth-service (decision ya tomada
 * para reducir acoplamiento sincrono). En este repo solo se GENERA el token;
 * los otros repos (room-service, realtime-service, etc.) solo lo VALIDAN,
 * reutilizando una clase equivalente a esta.
 */
@Component
public class JwtUtil {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtUtil(
            @Value("${orcalab.jwt.secret}") String secret,
            @Value("${orcalab.jwt.expiration-ms}") long expirationMs
    ) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes());
        this.expirationMs = expirationMs;
    }

    public String generateToken(String subjectEmail, String role, Long userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(subjectEmail)
                .claims(Map.of(
                        "role", role,
                        "userId", userId
                ))
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
