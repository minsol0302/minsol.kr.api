package kr.minsol.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;

@SpringBootApplication(exclude = { RedisAutoConfiguration.class })
public class ApiApplication {

	public static void main(String[] args) {
		// 환경 변수 검증
		String datasourceUrl = System.getenv("SPRING_DATASOURCE_URL");
		if (datasourceUrl == null || datasourceUrl.isEmpty()) {
			System.err.println("⚠️ 경고: SPRING_DATASOURCE_URL 환경 변수가 설정되지 않았습니다.");
			System.err.println("⚠️ 데이터베이스 연결에 실패할 수 있습니다.");
			System.err.println("⚠️ 환경 변수를 확인하거나 .env 파일을 확인하세요.");
		}

		SpringApplication.run(ApiApplication.class, args);
	}

}
