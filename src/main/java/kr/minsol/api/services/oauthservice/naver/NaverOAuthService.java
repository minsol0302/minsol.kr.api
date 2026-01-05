package kr.minsol.api.services.oauthservice.naver;

import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * 네이버 OAuth2 인증 서비스
 * 네이버 API와 통신하여 토큰 교환 및 사용자 정보 조회
 */
@Service
public class NaverOAuthService {

    private final RestTemplate restTemplate;

    // 네이버 API 엔드포인트
    private static final String NAVER_TOKEN_URL = "https://nid.naver.com/oauth2.0/token";
    private static final String NAVER_USER_INFO_URL = "https://openapi.naver.com/v1/nid/me";

    public NaverOAuthService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Authorization Code를 Access Token으로 교환
     * 
     * @param code  Authorization Code
     * @param state State 값 (CSRF 방지)
     * @return 네이버 토큰 응답 (access_token, refresh_token, expires_in 등)
     */
    public Map<String, Object> getAccessToken(String code, String state) {
        System.out.println("=== 네이버 Access Token 요청 ===");
        System.out.println("Authorization Code: " + code);
        System.out.println("State: " + state);

        // 환경 변수에서 가져오기
        String clientId = System.getenv("NAVER_CLIENT_ID");
        String clientSecret = System.getenv("NAVER_CLIENT_SECRET");
        String redirectUri = System.getenv("NAVER_REDIRECT_URI");

        // 요청 파라미터 설정
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", clientId);
        params.add("client_secret", clientSecret);
        params.add("redirect_uri", redirectUri);
        params.add("code", code);
        if (state != null) {
            params.add("state", state);
        }

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            // 네이버 토큰 API 호출
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.postForEntity(
                    NAVER_TOKEN_URL,
                    request,
                    Map.class);

            System.out.println("네이버 토큰 응답: " + response.getBody());
            System.out.println("================================");

            @SuppressWarnings("unchecked")
            Map<String, Object> body = response.getBody();
            return body;
        } catch (Exception e) {
            System.err.println("네이버 토큰 요청 실패: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("네이버 토큰 요청 실패", e);
        }
    }

    /**
     * Access Token으로 네이버 사용자 정보 조회
     * 
     * @param accessToken 네이버 Access Token
     * @return 사용자 정보 (id, name, email 등)
     */
    public Map<String, Object> getUserInfo(String accessToken) {
        System.out.println("=== 네이버 사용자 정보 요청 ===");
        System.out.println("Access Token: " + accessToken.substring(0, Math.min(accessToken.length(), 20)) + "...");

        // 헤더 설정
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<String> request = new HttpEntity<>(headers);

        try {
            // 네이버 사용자 정보 API 호출
            @SuppressWarnings("rawtypes")
            ResponseEntity<Map> response = restTemplate.exchange(
                    NAVER_USER_INFO_URL,
                    HttpMethod.GET,
                    request,
                    Map.class);

            System.out.println("네이버 사용자 정보 응답: " + response.getBody());
            System.out.println("================================");

            @SuppressWarnings("unchecked")
            Map<String, Object> body = response.getBody();
            return body;
        } catch (Exception e) {
            System.err.println("네이버 사용자 정보 요청 실패: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("네이버 사용자 정보 요청 실패", e);
        }
    }

    /**
     * 네이버 사용자 정보에서 필요한 데이터 추출
     * 
     * @param userInfo 네이버 API 응답
     * @return 추출된 사용자 정보
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> extractUserInfo(Map<String, Object> userInfo) {
        // 네이버 API 응답 구조: { "resultcode": "00", "message": "success", "response": { ...
        // } }
        Map<String, Object> response = (Map<String, Object>) userInfo.get("response");

        if (response == null) {
            throw new RuntimeException("네이버 사용자 정보 응답이 올바르지 않습니다.");
        }

        // 네이버 사용자 ID
        String naverId = (String) response.get("id");

        // 이름
        String name = (String) response.get("name");

        // 이메일
        String email = (String) response.get("email");

        // 프로필 이미지
        String profileImage = (String) response.get("profile_image");

        // 이메일 인증 여부 (네이버는 항상 인증된 이메일)
        Boolean emailVerified = true;

        // Map.of는 null을 허용하지 않으므로 HashMap 사용
        Map<String, Object> result = new HashMap<>();
        result.put("naver_id", naverId != null ? naverId : "");
        result.put("nickname", name != null ? name : "네이버 사용자");
        result.put("email", email != null ? email : "");
        result.put("email_verified", emailVerified != null ? emailVerified : false);
        result.put("profile_image", profileImage != null ? profileImage : "");

        return result;
    }
}
