package kr.minsol.api.services.oauthservice.token;

import kr.minsol.api.services.oauthservice.token.entity.OAuthToken;
import kr.minsol.api.services.oauthservice.token.repository.OAuthTokenRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class TokenService {
    private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;
    private final OAuthTokenRepository oAuthTokenRepository;

    public TokenService(
            ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider,
            @Autowired(required = false) OAuthTokenRepository oAuthTokenRepository) {
        this.redisTemplateProvider = redisTemplateProvider;
        this.oAuthTokenRepository = oAuthTokenRepository;
        if (redisTemplateProvider.getIfAvailable() == null) {
            System.out.println("⚠️ RedisTemplate이 없습니다. TokenService는 메모리 모드로 동작합니다.");
        }
        if (oAuthTokenRepository == null) {
            System.out.println("⚠️ OAuthTokenRepository가 없습니다. Neon 저장이 건너뜁니다.");
        }
    }

    private Optional<RedisTemplate<String, Object>> getRedisTemplate() {
        return Optional.ofNullable(redisTemplateProvider.getIfAvailable());
    }

    /**
     * Neon(PostgreSQL)에 토큰 저장
     */
    @Transactional
    private void saveTokenToNeon(String provider, String userId,
            String oauthAccessToken, String oauthRefreshToken,
            String jwtAccessToken, String jwtRefreshToken,
            long oauthExpireTime, long jwtAccessExpireTime, long jwtRefreshExpireTime) {
        if (oAuthTokenRepository == null) {
            return;
        }

        try {
            Optional<OAuthToken> existingToken = oAuthTokenRepository.findByProviderAndUserId(provider, userId);

            OAuthToken token;
            if (existingToken.isPresent()) {
                token = existingToken.get();
            } else {
                token = new OAuthToken();
                token.setProvider(provider);
                token.setUserId(userId);
            }

            token.setOauthAccessToken(oauthAccessToken != null ? oauthAccessToken : token.getOauthAccessToken());
            if (oauthRefreshToken != null) {
                token.setOauthRefreshToken(oauthRefreshToken);
            }
            token.setJwtAccessToken(jwtAccessToken);
            token.setJwtRefreshToken(jwtRefreshToken);

            LocalDateTime now = LocalDateTime.now();
            if (oauthExpireTime > 0) {
                token.setExpiresAt(now.plusSeconds(oauthExpireTime));
            }
            token.setJwtAccessTokenExpiresAt(now.plusSeconds(jwtAccessExpireTime));
            token.setJwtRefreshTokenExpiresAt(now.plusSeconds(jwtRefreshExpireTime));

            oAuthTokenRepository.save(token);
            System.out.println("✅ Neon 저장 완료 - Provider: " + provider + ", UserId: " + userId);
        } catch (Exception e) {
            System.err.println("⚠️ Neon 저장 실패 (계속 진행): " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Access Token 저장 (Redis에만 저장)
     * 
     * @param provider    소셜 로그인 제공자 (kakao, naver, google)
     * @param userId      사용자 ID
     * @param accessToken Access Token
     * @param expireTime  만료 시간 (초)
     */
    public void saveAccessToken(String provider, String userId, String accessToken, long expireTime) {
        Optional<RedisTemplate<String, Object>> redisTemplate = getRedisTemplate();
        if (redisTemplate.isPresent()) {
            try {
                String key = String.format("token:%s:%s:access", provider, userId);
                redisTemplate.get().opsForValue().set(key, accessToken, expireTime, TimeUnit.SECONDS);
                System.out.println("✅ Redis 저장 - JWT Access Token - Key: " + key + ", TTL: " + expireTime + "초");
            } catch (Exception e) {
                System.err.println("⚠️ Redis 저장 실패 (계속 진행): Access Token 저장 중 오류 - " + e.getMessage());
            }
        }
    }

    /**
     * Refresh Token 저장 (Redis에만 저장)
     * 
     * @param provider     소셜 로그인 제공자 (kakao, naver, google)
     * @param userId       사용자 ID
     * @param refreshToken Refresh Token
     * @param expireTime   만료 시간 (초)
     */
    public void saveRefreshToken(String provider, String userId, String refreshToken, long expireTime) {
        Optional<RedisTemplate<String, Object>> redisTemplate = getRedisTemplate();
        if (redisTemplate.isPresent()) {
            try {
                String key = String.format("token:%s:%s:refresh", provider, userId);
                redisTemplate.get().opsForValue().set(key, refreshToken, expireTime, TimeUnit.SECONDS);
                System.out.println("✅ Redis 저장 - JWT Refresh Token - Key: " + key + ", TTL: " + expireTime + "초");
            } catch (Exception e) {
                System.err.println("⚠️ Redis 저장 실패 (계속 진행): Refresh Token 저장 중 오류 - " + e.getMessage());
            }
        }
    }

    /**
     * Access Token 조회
     * 
     * @param provider 소셜 로그인 제공자
     * @param userId   사용자 ID
     * @return Access Token
     */
    public String getAccessToken(String provider, String userId) {
        Optional<RedisTemplate<String, Object>> redisTemplate = getRedisTemplate();
        if (redisTemplate.isPresent()) {
            String key = String.format("token:%s:%s:access", provider, userId);
            Object token = redisTemplate.get().opsForValue().get(key);
            return token != null ? token.toString() : null;
        }
        return null;
    }

    /**
     * Refresh Token 조회
     * 
     * @param provider 소셜 로그인 제공자
     * @param userId   사용자 ID
     * @return Refresh Token
     */
    public String getRefreshToken(String provider, String userId) {
        Optional<RedisTemplate<String, Object>> redisTemplate = getRedisTemplate();
        if (redisTemplate.isPresent()) {
            String key = String.format("token:%s:%s:refresh", provider, userId);
            Object token = redisTemplate.get().opsForValue().get(key);
            return token != null ? token.toString() : null;
        }
        return null;
    }

    /**
     * 토큰 삭제
     * 
     * @param provider 소셜 로그인 제공자
     * @param userId   사용자 ID
     */
    public void deleteTokens(String provider, String userId) {
        Optional<RedisTemplate<String, Object>> redisTemplate = getRedisTemplate();
        if (redisTemplate.isPresent()) {
            String accessKey = String.format("token:%s:%s:access", provider, userId);
            String refreshKey = String.format("token:%s:%s:refresh", provider, userId);
            redisTemplate.get().delete(accessKey);
            redisTemplate.get().delete(refreshKey);
        }
    }

    /**
     * Authorization Code 저장 (임시 저장용)
     * 
     * @param provider   소셜 로그인 제공자
     * @param code       Authorization Code
     * @param expireTime 만료 시간 (초, 기본 10분)
     */
    public void saveAuthorizationCode(String provider, String code, String state, long expireTime) {
        Optional<RedisTemplate<String, Object>> redisTemplate = getRedisTemplate();
        if (redisTemplate.isPresent()) {
            String key = String.format("code:%s:%s", provider, code);
            redisTemplate.get().opsForValue().set(key, state != null ? state : "", expireTime, TimeUnit.SECONDS);
        }
    }

    /**
     * Authorization Code 검증 및 삭제
     * 
     * @param provider 소셜 로그인 제공자
     * @param code     Authorization Code
     * @return state 값 (있으면 반환, 없으면 null)
     */
    public String verifyAndDeleteAuthorizationCode(String provider, String code) {
        Optional<RedisTemplate<String, Object>> redisTemplate = getRedisTemplate();
        if (redisTemplate.isPresent()) {
            String key = String.format("code:%s:%s", provider, code);
            Object state = redisTemplate.get().opsForValue().get(key);
            if (state != null) {
                redisTemplate.get().delete(key);
                return state.toString();
            }
        }
        return null;
    }

    /**
     * OAuth 제공자 원본 Access Token 저장 (구글, 카카오 등에서 받은 토큰)
     * Redis와 Neon 모두에 저장합니다.
     * 
     * @param provider    소셜 로그인 제공자 (kakao, naver, google)
     * @param userId      사용자 ID
     * @param accessToken OAuth 제공자에서 받은 원본 Access Token
     * @param expireTime  만료 시간 (초)
     */
    public void saveOAuthAccessToken(String provider, String userId, String accessToken, long expireTime) {
        // Redis 저장
        Optional<RedisTemplate<String, Object>> redisTemplate = getRedisTemplate();
        if (redisTemplate.isPresent()) {
            try {
                String key = String.format("oauth:%s:%s:access", provider, userId);
                redisTemplate.get().opsForValue().set(key, accessToken, expireTime, TimeUnit.SECONDS);
                System.out.println("✅ Redis 저장 - OAuth Access Token - Key: " + key + ", TTL: " + expireTime + "초");
            } catch (Exception e) {
                System.err.println("⚠️ Redis 저장 실패 (계속 진행): OAuth Access Token 저장 중 오류 - " + e.getMessage());
            }
        }
    }

    /**
     * OAuth 제공자 원본 Refresh Token 저장 (구글, 카카오 등에서 받은 토큰)
     * Redis에 저장합니다.
     * 
     * @param provider     소셜 로그인 제공자 (kakao, naver, google)
     * @param userId       사용자 ID
     * @param refreshToken OAuth 제공자에서 받은 원본 Refresh Token
     * @param expireTime   만료 시간 (초)
     */
    public void saveOAuthRefreshToken(String provider, String userId, String refreshToken, long expireTime) {
        // Redis 저장
        Optional<RedisTemplate<String, Object>> redisTemplate = getRedisTemplate();
        if (redisTemplate.isPresent()) {
            try {
                String key = String.format("oauth:%s:%s:refresh", provider, userId);
                redisTemplate.get().opsForValue().set(key, refreshToken, expireTime, TimeUnit.SECONDS);
                System.out.println("✅ Redis 저장 - OAuth Refresh Token - Key: " + key + ", TTL: " + expireTime + "초");
            } catch (Exception e) {
                System.err.println("⚠️ Redis 저장 실패 (계속 진행): OAuth Refresh Token 저장 중 오류 - " + e.getMessage());
            }
        }
    }

    /**
     * 모든 토큰을 Redis와 Neon에 저장 (통합 저장 메서드)
     * 
     * @param provider             OAuth 제공자
     * @param userId               사용자 ID
     * @param oauthAccessToken     OAuth Access Token
     * @param oauthRefreshToken    OAuth Refresh Token
     * @param jwtAccessToken       JWT Access Token
     * @param jwtRefreshToken      JWT Refresh Token
     * @param oauthExpireTime      OAuth 토큰 만료 시간 (초)
     * @param jwtAccessExpireTime  JWT Access Token 만료 시간 (초)
     * @param jwtRefreshExpireTime JWT Refresh Token 만료 시간 (초)
     */
    public void saveAllTokens(String provider, String userId,
            String oauthAccessToken, String oauthRefreshToken,
            String jwtAccessToken, String jwtRefreshToken,
            long oauthExpireTime, long jwtAccessExpireTime, long jwtRefreshExpireTime) {
        // Redis 저장
        if (oauthAccessToken != null) {
            saveOAuthAccessToken(provider, userId, oauthAccessToken, oauthExpireTime);
        }
        if (oauthRefreshToken != null) {
            saveOAuthRefreshToken(provider, userId, oauthRefreshToken, oauthExpireTime);
        }
        saveAccessToken(provider, userId, jwtAccessToken, jwtAccessExpireTime);
        saveRefreshToken(provider, userId, jwtRefreshToken, jwtRefreshExpireTime);

        // Neon 저장 (통합 저장)
        saveTokenToNeon(provider, userId, oauthAccessToken, oauthRefreshToken,
                jwtAccessToken, jwtRefreshToken,
                oauthExpireTime, jwtAccessExpireTime, jwtRefreshExpireTime);
    }

    /**
     * OAuth 제공자 원본 Access Token 조회
     * 
     * @param provider 소셜 로그인 제공자
     * @param userId   사용자 ID
     * @return OAuth Access Token
     */
    public String getOAuthAccessToken(String provider, String userId) {
        Optional<RedisTemplate<String, Object>> redisTemplate = getRedisTemplate();
        if (redisTemplate.isPresent()) {
            String key = String.format("oauth:%s:%s:access", provider, userId);
            Object token = redisTemplate.get().opsForValue().get(key);
            return token != null ? token.toString() : null;
        }
        return null;
    }

    /**
     * OAuth 제공자 원본 Refresh Token 조회
     * 
     * @param provider 소셜 로그인 제공자
     * @param userId   사용자 ID
     * @return OAuth Refresh Token
     */
    public String getOAuthRefreshToken(String provider, String userId) {
        Optional<RedisTemplate<String, Object>> redisTemplate = getRedisTemplate();
        if (redisTemplate.isPresent()) {
            String key = String.format("oauth:%s:%s:refresh", provider, userId);
            Object token = redisTemplate.get().opsForValue().get(key);
            return token != null ? token.toString() : null;
        }
        return null;
    }

    /**
     * OAuth 제공자 원본 토큰 삭제
     * 
     * @param provider 소셜 로그인 제공자
     * @param userId   사용자 ID
     */
    public void deleteOAuthTokens(String provider, String userId) {
        Optional<RedisTemplate<String, Object>> redisTemplate = getRedisTemplate();
        if (redisTemplate.isPresent()) {
            String accessKey = String.format("oauth:%s:%s:access", provider, userId);
            String refreshKey = String.format("oauth:%s:%s:refresh", provider, userId);
            redisTemplate.get().delete(accessKey);
            redisTemplate.get().delete(refreshKey);
        }
    }
}
