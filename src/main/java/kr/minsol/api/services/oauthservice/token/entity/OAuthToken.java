package kr.minsol.api.services.oauthservice.token.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * OAuth 토큰을 Neon(PostgreSQL)에 저장하기 위한 엔티티
 */
@Entity
@Table(name = "oauth_tokens", indexes = {
    @Index(name = "idx_provider_user", columnList = "provider,userId"),
    @Index(name = "idx_expires_at", columnList = "expiresAt")
})
public class OAuthToken {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, length = 50)
    private String provider; // google, kakao, naver
    
    @Column(nullable = false, length = 255)
    private String userId;
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String oauthAccessToken; // OAuth 제공자에서 받은 원본 Access Token
    
    @Column(columnDefinition = "TEXT")
    private String oauthRefreshToken; // OAuth 제공자에서 받은 원본 Refresh Token
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String jwtAccessToken; // 자체 JWT Access Token
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String jwtRefreshToken; // 자체 JWT Refresh Token
    
    @Column(nullable = false)
    private LocalDateTime expiresAt; // OAuth Access Token 만료 시간
    
    @Column(nullable = false)
    private LocalDateTime jwtAccessTokenExpiresAt; // JWT Access Token 만료 시간
    
    @Column(nullable = false)
    private LocalDateTime jwtRefreshTokenExpiresAt; // JWT Refresh Token 만료 시간
    
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getProvider() {
        return provider;
    }
    
    public void setProvider(String provider) {
        this.provider = provider;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getOauthAccessToken() {
        return oauthAccessToken;
    }
    
    public void setOauthAccessToken(String oauthAccessToken) {
        this.oauthAccessToken = oauthAccessToken;
    }
    
    public String getOauthRefreshToken() {
        return oauthRefreshToken;
    }
    
    public void setOauthRefreshToken(String oauthRefreshToken) {
        this.oauthRefreshToken = oauthRefreshToken;
    }
    
    public String getJwtAccessToken() {
        return jwtAccessToken;
    }
    
    public void setJwtAccessToken(String jwtAccessToken) {
        this.jwtAccessToken = jwtAccessToken;
    }
    
    public String getJwtRefreshToken() {
        return jwtRefreshToken;
    }
    
    public void setJwtRefreshToken(String jwtRefreshToken) {
        this.jwtRefreshToken = jwtRefreshToken;
    }
    
    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }
    
    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }
    
    public LocalDateTime getJwtAccessTokenExpiresAt() {
        return jwtAccessTokenExpiresAt;
    }
    
    public void setJwtAccessTokenExpiresAt(LocalDateTime jwtAccessTokenExpiresAt) {
        this.jwtAccessTokenExpiresAt = jwtAccessTokenExpiresAt;
    }
    
    public LocalDateTime getJwtRefreshTokenExpiresAt() {
        return jwtRefreshTokenExpiresAt;
    }
    
    public void setJwtRefreshTokenExpiresAt(LocalDateTime jwtRefreshTokenExpiresAt) {
        this.jwtRefreshTokenExpiresAt = jwtRefreshTokenExpiresAt;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

