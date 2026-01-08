package kr.minsol.api.services.oauthservice.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 데이터베이스 초기화 컴포넌트
 * 애플리케이션 시작 시 oauth_tokens 테이블이 없으면 자동으로 생성합니다.
 */
@Component
public class DatabaseInitializer {

    private static final Logger logger = LoggerFactory.getLogger(DatabaseInitializer.class);

    @Autowired(required = false)
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void initializeDatabase() {
        if (jdbcTemplate == null) {
            logger.warn("JdbcTemplate이 없습니다. 데이터베이스 초기화를 건너뜁니다.");
            return;
        }

        try {
            // oauth_tokens 테이블 존재 여부 확인
            String checkTableSql = """
                SELECT EXISTS (
                    SELECT FROM information_schema.tables 
                    WHERE table_schema = 'public' 
                    AND table_name = 'oauth_tokens'
                );
                """;

            Boolean tableExists = jdbcTemplate.queryForObject(checkTableSql, Boolean.class);

            if (Boolean.FALSE.equals(tableExists)) {
                logger.info("oauth_tokens 테이블이 없습니다. 테이블을 생성합니다...");
                createOAuthTokensTable();
                logger.info("✅ oauth_tokens 테이블 생성 완료");
            } else {
                logger.info("✅ oauth_tokens 테이블이 이미 존재합니다.");
            }
        } catch (Exception e) {
            logger.error("⚠️ 데이터베이스 초기화 중 오류 발생: " + e.getMessage(), e);
            // 오류가 발생해도 애플리케이션은 계속 실행되도록 함
        }
    }

    private void createOAuthTokensTable() {
        String createTableSql = """
            CREATE TABLE IF NOT EXISTS oauth_tokens (
                id BIGSERIAL PRIMARY KEY,
                provider VARCHAR(50) NOT NULL,
                user_id VARCHAR(255) NOT NULL,
                oauth_access_token TEXT NOT NULL,
                oauth_refresh_token TEXT,
                jwt_access_token TEXT NOT NULL,
                jwt_refresh_token TEXT NOT NULL,
                expires_at TIMESTAMP NOT NULL,
                jwt_access_token_expires_at TIMESTAMP NOT NULL,
                jwt_refresh_token_expires_at TIMESTAMP NOT NULL,
                created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                CONSTRAINT uq_provider_user UNIQUE (provider, user_id)
            );
            """;

        String createIndex1Sql = """
            CREATE INDEX IF NOT EXISTS idx_provider_user ON oauth_tokens(provider, user_id);
            """;

        String createIndex2Sql = """
            CREATE INDEX IF NOT EXISTS idx_expires_at ON oauth_tokens(expires_at);
            """;

        String createTriggerFunctionSql = """
            CREATE OR REPLACE FUNCTION update_updated_at_column()
            RETURNS TRIGGER AS $$
            BEGIN
                NEW.updated_at = CURRENT_TIMESTAMP;
                RETURN NEW;
            END;
            $$ language 'plpgsql';
            """;

        String createTriggerSql = """
            DROP TRIGGER IF EXISTS update_oauth_tokens_updated_at ON oauth_tokens;
            CREATE TRIGGER update_oauth_tokens_updated_at
                BEFORE UPDATE ON oauth_tokens
                FOR EACH ROW
                EXECUTE FUNCTION update_updated_at_column();
            """;

        try {
            jdbcTemplate.execute(createTableSql);
            jdbcTemplate.execute(createIndex1Sql);
            jdbcTemplate.execute(createIndex2Sql);
            jdbcTemplate.execute(createTriggerFunctionSql);
            jdbcTemplate.execute(createTriggerSql);
        } catch (Exception e) {
            logger.error("테이블 생성 중 오류: " + e.getMessage(), e);
            throw e;
        }
    }
}
