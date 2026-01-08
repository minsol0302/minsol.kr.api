-- OAuth 토큰 테이블 생성
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

-- 인덱스 생성
CREATE INDEX IF NOT EXISTS idx_provider_user ON oauth_tokens(provider, user_id);
CREATE INDEX IF NOT EXISTS idx_expires_at ON oauth_tokens(expires_at);

-- updated_at 자동 업데이트 트리거 함수
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- updated_at 자동 업데이트 트리거
DROP TRIGGER IF EXISTS update_oauth_tokens_updated_at ON oauth_tokens;
CREATE TRIGGER update_oauth_tokens_updated_at
    BEFORE UPDATE ON oauth_tokens
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
