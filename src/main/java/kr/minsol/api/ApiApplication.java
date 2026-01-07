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
			System.err.println("⚠️ GitHub Secrets에 SPRING_DATASOURCE_URL을 설정해주세요.");
			System.err.println("⚠️ 또는 DATABASE_URL을 설정하면 자동으로 SPRING_DATASOURCE_URL로 변환됩니다.");
			System.err.println("⚠️ 애플리케이션이 시작되지 않을 수 있습니다.");
		}

		SpringApplication.run(ApiApplication.class, args);
	}

}
