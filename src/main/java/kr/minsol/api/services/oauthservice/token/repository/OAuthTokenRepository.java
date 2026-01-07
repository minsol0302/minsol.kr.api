package kr.minsol.api.services.oauthservice.token.repository;

import kr.minsol.api.services.oauthservice.token.entity.OAuthToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * OAuth 토큰을 Neon(PostgreSQL)에 저장하기 위한 리포지토리
 */
@Repository
public interface OAuthTokenRepository extends JpaRepository<OAuthToken, Long> {
    
    /**
     * provider와 userId로 토큰 조회
     */
    Optional<OAuthToken> findByProviderAndUserId(String provider, String userId);
    
    /**
     * 만료된 토큰 삭제
     */
    @Modifying
    @Query("DELETE FROM OAuthToken t WHERE t.expiresAt < :now")
    void deleteExpiredTokens(@Param("now") LocalDateTime now);
    
    /**
     * provider와 userId로 토큰 삭제
     */
    void deleteByProviderAndUserId(String provider, String userId);
}

