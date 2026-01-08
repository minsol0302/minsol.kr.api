package kr.minsol.api.services.oauthservice.config;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.SslOptions;
import io.netty.handler.ssl.util.InsecureTrustManagerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * Redis 설정
 * Upstash Redis 연결 (ACL 기반 - username 필수)
 * Redis가 없어도 애플리케이션이 시작되도록 선택적으로 설정됩니다.
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true", matchIfMissing = false)
public class RedisConfig {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.username:default}")
    private String redisUsername;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${spring.data.redis.ssl.enabled:false}")
    private boolean sslEnabled;

    /**
     * RedisConnectionFactory 빈 생성
     * Upstash Redis는 ACL 기반이므로 username=default를 필수로 설정합니다.
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        try {
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            
            // Upstash Redis 설정 (ACL 기반 - username 필수)
            config.setHostName(redisHost);
            config.setPort(redisPort);
            config.setUsername(redisUsername); // ⭐ 핵심: Upstash는 username 필수
            if (redisPassword != null && !redisPassword.isEmpty()) {
                config.setPassword(redisPassword);
            }
            
            System.out.println("✅ Redis 설정 완료 - Host: " + redisHost + ", Port: " + redisPort 
                    + ", Username: " + redisUsername + ", SSL: " + sslEnabled);

            // LettuceClientConfiguration 빌더
            LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfigBuilder = LettuceClientConfiguration
                    .builder()
                    .commandTimeout(Duration.ofSeconds(5))
                    .shutdownTimeout(Duration.ZERO);

            // SSL 설정 (Upstash Redis용 - 필수)
            if (sslEnabled) {
                try {
                    SslOptions sslOptions = SslOptions.builder()
                            .jdkSslProvider()
                            .trustManager(InsecureTrustManagerFactory.INSTANCE) // Upstash는 자체 서명 인증서 사용
                            .build();

                    SocketOptions socketOptions = SocketOptions.builder()
                            .connectTimeout(Duration.ofSeconds(5))
                            .keepAlive(true)
                            .build();

                    ClientOptions clientOptions = ClientOptions.builder()
                            .socketOptions(socketOptions)
                            .sslOptions(sslOptions)
                            .build();

                    clientConfigBuilder.clientOptions(clientOptions);
                    System.out.println("✅ Redis SSL 설정 완료");
                } catch (Exception e) {
                    System.err.println("⚠️ Redis SSL 설정 실패: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfigBuilder.build());
            factory.afterPropertiesSet();
            System.out.println("✅ Redis ConnectionFactory 생성 완료");
            return factory;
        } catch (Exception e) {
            System.err.println("⚠️ Redis ConnectionFactory 생성 실패: " + e.getMessage());
            e.printStackTrace();
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
                Thread.sleep(3000); // 3초 대기 후 테스트 (애플리케이션 시작 후)
                // 연결 테스트 (재시도 로직 포함)
                int maxRetries = 3;
                for (int i = 0; i < maxRetries; i++) {
                    try {
                        template.getConnectionFactory().getConnection().ping();
                        template.opsForValue().set("connection:test", "ok", 10, java.util.concurrent.TimeUnit.SECONDS);
                        System.out.println("✅ Redis 연결 성공");
                        return;
                    } catch (Exception e) {
                        if (i < maxRetries - 1) {
                            System.out.println("⚠️ Redis 연결 재시도 " + (i + 1) + "/" + maxRetries + ": " + e.getMessage());
                            Thread.sleep(2000); // 2초 대기 후 재시도
                        } else {
                            System.err.println("⚠️ Redis 연결 실패 (계속 진행): " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("⚠️ Redis 연결 테스트 중 오류 (계속 진행): " + e.getMessage());
                // Redis 연결 실패해도 애플리케이션은 계속 실행되도록 함
            }
        }).start();

        return template;
    }
}