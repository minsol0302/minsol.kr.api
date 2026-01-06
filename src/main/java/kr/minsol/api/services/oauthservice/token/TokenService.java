package kr.minsol.api.services.oauthservice.token;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class TokenService {
    private final ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider;

    public TokenService(ObjectProvider<RedisTemplate<String, Object>> redisTemplateProvider) {
        this.redisTemplateProvider = redisTemplateProvider;
        if (redisTemplateProvider.getIfAvailable() == null) {
            System.out.println("⚠️ RedisTemplate이 없습니다. TokenService는 메모리 모드로 동작합니다.");
        }
    }

    private Optional<RedisTemplate<String, Object>> getRedisTemplate() {
        return Optional.ofNullable(redisTemplateProvider.getIfAvailable());
    }

    /**
     * Access Token 저장
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
                System.out.println("Redis 저장 - Key: " + key + ", TTL: " + expireTime + "초");
            } catch (Exception e) {
                System.err.println("⚠️ Redis 저장 실패 (계속 진행): Access Token 저장 중 오류 - " + e.getMessage());
                // Redis 저장 실패해도 로그인은 계속 진행
            }
        } else {
            System.out.println("⚠️ Redis가 비활성화되어 있습니다. 토큰 저장이 건너뜁니다.");
        }
    }

    /**
     * Refresh Token 저장
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
                System.out.println("Redis 저장 - Key: " + key + ", TTL: " + expireTime + "초");
            } catch (Exception e) {
                System.err.println("⚠️ Redis 저장 실패 (계속 진행): Refresh Token 저장 중 오류 - " + e.getMessage());
                // Redis 저장 실패해도 로그인은 계속 진행
            }
        } else {
            System.out.println("⚠️ Redis가 비활성화되어 있습니다. 토큰 저장이 건너뜁니다.");
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
     * 
     * @param provider    소셜 로그인 제공자 (kakao, naver, google)
     * @param userId      사용자 ID
     * @param accessToken OAuth 제공자에서 받은 원본 Access Token
     * @param expireTime  만료 시간 (초)
     */
    public void saveOAuthAccessToken(String provider, String userId, String accessToken, long expireTime) {
        Optional<RedisTemplate<String, Object>> redisTemplate = getRedisTemplate();
        if (redisTemplate.isPresent()) {
            try {
                String key = String.format("oauth:%s:%s:access", provider, userId);
                redisTemplate.get().opsForValue().set(key, accessToken, expireTime, TimeUnit.SECONDS);
                System.out.println("Redis 저장 - OAuth Access Token - Key: " + key + ", TTL: " + expireTime + "초");
            } catch (Exception e) {
                System.err.println("⚠️ Redis 저장 실패 (계속 진행): OAuth Access Token 저장 중 오류 - " + e.getMessage());
                // Redis 저장 실패해도 로그인은 계속 진행
            }
        } else {
            System.out.println("⚠️ Redis가 비활성화되어 있습니다. OAuth 토큰 저장이 건너뜁니다.");
        }
    }

    /**
     * OAuth 제공자 원본 Refresh Token 저장 (구글, 카카오 등에서 받은 토큰)
     * 
     * @param provider     소셜 로그인 제공자 (kakao, naver, google)
     * @param userId       사용자 ID
     * @param refreshToken OAuth 제공자에서 받은 원본 Refresh Token
     * @param expireTime   만료 시간 (초)
     */
    public void saveOAuthRefreshToken(String provider, String userId, String refreshToken, long expireTime) {
        Optional<RedisTemplate<String, Object>> redisTemplate = getRedisTemplate();
        if (redisTemplate.isPresent()) {
            try {
                String key = String.format("oauth:%s:%s:refresh", provider, userId);
                redisTemplate.get().opsForValue().set(key, refreshToken, expireTime, TimeUnit.SECONDS);
                System.out.println("Redis 저장 - OAuth Refresh Token - Key: " + key + ", TTL: " + expireTime + "초");
            } catch (Exception e) {
                System.err.println("⚠️ Redis 저장 실패 (계속 진행): OAuth Refresh Token 저장 중 오류 - " + e.getMessage());
                // Redis 저장 실패해도 로그인은 계속 진행
            }
        } else {
            System.out.println("⚠️ Redis가 비활성화되어 있습니다. OAuth 토큰 저장이 건너뜁니다.");
        }
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
