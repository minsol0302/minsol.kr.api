package kr.minsol.api.oauthservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 설정
 * 로컬 Redis와 Google Cloud Memorystore Redis를 모두 지원합니다.
 * Redis가 없어도 애플리케이션이 시작되도록 선택적으로 설정됩니다.
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true", matchIfMissing = false)
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    /**
     * RedisConnectionFactory 빈 생성
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        try {
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName(redisHost);
            config.setPort(redisPort);
            if (redisPassword != null && !redisPassword.isEmpty()) {
                config.setPassword(redisPassword);
            }
            if (sslEnabled) {
                System.out.println("Redis SSL 연결 시도: " + redisHost + ":" + redisPort);
            } else {
                System.out.println("Redis 일반 연결 시도: " + redisHost + ":" + redisPort);
            }
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config);
            factory.afterPropertiesSet();
            return factory;
        } catch (Exception e) {
            System.err.println("⚠️ Redis ConnectionFactory 생성 실패: " + e.getMessage());
            throw e;
        }
    }

    /**
     * RedisTemplate 빈 생성
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key는 String으로 직렬화
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // Value는 JSON으로 직렬화
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();

        // 연결 정보 출력
        if (sslEnabled) {
            System.out.println("Redis SSL 연결 활성화: " + redisHost + ":" + redisPort);
        } else {
            System.out.println("Redis 일반 연결: " + redisHost + ":" + redisPort);
        }

        // 연결 테스트는 별도 스레드에서 비동기로 처리하여 애플리케이션 시작을 막지 않음
        new Thread(() -> {
            try {
                Thread.sleep(2000); // 2초 대기 후 테스트 (애플리케이션 시작 후)
                template.opsForValue().set("connection:test", "ok", 10, java.util.concurrent.TimeUnit.SECONDS);
                System.out.println("✅ Redis 연결 성공");
            } catch (Exception e) {
                System.err.println("⚠️ Redis 연결 실패 (계속 진행): " + e.getMessage());
                // Redis 연결 실패해도 애플리케이션은 계속 실행되도록 함
            }
        }).start();

        return template;
    }
}