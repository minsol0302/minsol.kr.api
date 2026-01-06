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
     * 호스트에서 포트 제거 및 정리
     * 환경 변수에 "host:port" 형식으로 들어온 경우 분리
     */
    private void parseHostAndPort() {
        if (redisHost != null && redisHost.contains(":")) {
            // 호스트에 포트가 포함된 경우 분리
            String[] parts = redisHost.split(":");
            redisHost = parts[0];
            if (parts.length > 1) {
                try {
                    redisPort = Integer.parseInt(parts[1]);
                    System.out.println("Redis 호스트/포트 분리: 호스트=" + redisHost + ", 포트=" + redisPort);
                } catch (NumberFormatException e) {
                    System.err.println("⚠️ Redis 포트 파싱 실패: " + parts[1] + ", 기본값 6379 사용");
                }
            }
        }
    }

    /**
     * RedisConnectionFactory 빈 생성
     */
    @Bean
    public RedisConnectionFactory redisConnectionFactory() {
        try {
            // 호스트와 포트 분리
            parseHostAndPort();
            
            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration();
            config.setHostName(redisHost);
            config.setPort(redisPort);
            if (redisPassword != null && !redisPassword.isEmpty()) {
                config.setPassword(redisPassword);
            }
            
            LettuceClientConfiguration.LettuceClientConfigurationBuilder clientConfigBuilder = 
                LettuceClientConfiguration.builder()
                    .commandTimeout(Duration.ofSeconds(10))
                    .shutdownTimeout(Duration.ofMillis(100));
            
            // SSL 설정
            if (sslEnabled) {
                System.out.println("Redis SSL 연결 시도: " + redisHost + ":" + redisPort);
                try {
                    // Upstash Redis SSL 설정
                    SslOptions sslOptions = SslOptions.builder()
                        .jdkSslProvider()
                        .trustManager(InsecureTrustManagerFactory.INSTANCE) // Upstash는 자체 서명 인증서 사용
                        .build();
                    
                    SocketOptions socketOptions = SocketOptions.builder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .keepAlive(true)
                        .build();
                    
                    ClientOptions clientOptions = ClientOptions.builder()
                        .socketOptions(socketOptions)
                        .sslOptions(sslOptions)
                        .build();
                    
                    clientConfigBuilder.clientOptions(clientOptions);
                    System.out.println("Redis SSL 설정 완료");
                } catch (Exception e) {
                    System.err.println("⚠️ Redis SSL 설정 실패: " + e.getMessage());
                    e.printStackTrace();
                    throw new RuntimeException("Redis SSL 설정 실패", e);
                }
            } else {
                System.out.println("Redis 일반 연결 시도: " + redisHost + ":" + redisPort);
            }
            
            LettuceConnectionFactory factory = new LettuceConnectionFactory(config, clientConfigBuilder.build());
            factory.afterPropertiesSet();
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