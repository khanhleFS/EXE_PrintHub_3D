package com.fpt.printhub_3d.common.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtProperties jwtProperties;

    // ========== GENERATE TOKEN ==========

    public String generateAccessToken(UserDetails userDetails) {
        return generateAccessToken(new HashMap<>(), userDetails);
    }

    public String generateAccessToken(Map<String, Object> extraClaims, UserDetails userDetails) {
        return buildToken(extraClaims, userDetails, jwtProperties.getAccessTokenExpiration());
    }

    public String generateRefreshToken(UserDetails userDetails) {
        return buildToken(new HashMap<>(), userDetails, jwtProperties.getRefreshTokenExpiration());
    }

    private String buildToken(Map<String, Object> extraClaims, UserDetails userDetails, long expiration) {
        return Jwts.builder()
                .claims(extraClaims)                                          // 0.12.x: thay setClaims()
                .subject(userDetails.getUsername())                           // 0.12.x: thay setSubject()
                .issuedAt(new Date(System.currentTimeMillis()))               // 0.12.x: thay setIssuedAt()
                .expiration(new Date(System.currentTimeMillis() + expiration))// 0.12.x: thay setExpiration()
                .signWith(getSignKey())                                        // 0.12.x: không cần truyền algorithm
                .compact();
    }

    // ========== VALIDATE TOKEN ==========

    public boolean isTokenValid(String token, UserDetails userDetails) {
        final String username = extractUsername(token);
        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ========== EXTRACT CLAIMS ==========

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()                          // 0.12.x: thay parserBuilder()
                    .verifyWith(getSignKey())              // 0.12.x: thay setSigningKey()
                    .build()
                    .parseSignedClaims(token)             // 0.12.x: thay parseClaimsJws()
                    .getPayload();                        // 0.12.x: thay getBody()
        } catch (ExpiredJwtException e) {
            log.error("JWT expired: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            log.error("JWT unsupported: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            log.error("JWT malformed: {}", e.getMessage());
            throw e;
        } catch (io.jsonwebtoken.security.SignatureException e) {
            log.error("JWT signature invalid: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            log.error("JWT claims empty: {}", e.getMessage());
            throw e;
        }
    }

    // ========== SIGN KEY ==========

    private SecretKey getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret()); // secret phải là Base64
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
