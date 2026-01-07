package kr.minsol.api.services.oauthservice.google;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import kr.minsol.api.services.oauthservice.jwt.JwtTokenProvider;
import kr.minsol.api.services.oauthservice.jwt.JwtUtil;
import kr.minsol.api.services.oauthservice.token.TokenService;

@RestController
@RequestMapping({ "/google", "/api/auth/google", "/oauth2/google" })
public class GoogleController {

    private final TokenService tokenService;
    private final GoogleOAuthService googleOAuthService;
    private final JwtTokenProvider jwtTokenProvider;

    public GoogleController(
            TokenService tokenService,
            GoogleOAuthService googleOAuthService,
            JwtTokenProvider jwtTokenProvider) {
        this.tokenService = tokenService;
        this.googleOAuthService = googleOAuthService;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 구글 인증 URL 제공
     * 프론트엔드에서 CLIENT ID를 노출하지 않고 인증 URL을 가져올 수 있도록 함
     */
    @PostMapping("/auth-url")
    public ResponseEntity<Map<String, Object>> getGoogleAuthUrl(
            @RequestBody(required = false) Map<String, Object> request) {
        try {
            // 환경 변수에서 가져오기
            String clientId = System.getenv("GOOGLE_CLIENT_ID");
            String redirectUri = System.getenv("GOOGLE_REDIRECT_URI");

            // 환경 변수 검증
            if (clientId == null || clientId.isEmpty()) {
                System.err.println("[Google Auth-URL] 오류: GOOGLE_CLIENT_ID 환경 변수가 설정되지 않았습니다.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "success", false,
                        "error", "GOOGLE_CLIENT_ID 환경 변수가 설정되지 않았습니다.",
                        "message", "서버 설정 오류: Google Client ID가 없습니다."));
            }

            if (redirectUri == null || redirectUri.isEmpty()) {
                System.err.println("[Google Auth-URL] 오류: GOOGLE_REDIRECT_URI 환경 변수가 설정되지 않았습니다.");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                        "success", false,
                        "error", "GOOGLE_REDIRECT_URI 환경 변수가 설정되지 않았습니다.",
                        "message", "서버 설정 오류: Google Redirect URI가 없습니다."));
            }

            String state = UUID.randomUUID().toString(); // CSRF 방지용 state

            String authUrl = String.format(
                    "https://accounts.google.com/o/oauth2/v2/auth?response_type=code&client_id=%s&redirect_uri=%s&scope=openid%%20profile%%20email&state=%s",
                    clientId,
                    URLEncoder.encode(redirectUri, StandardCharsets.UTF_8),
                    state);

            System.out.println(
                    "[Google Auth-URL] 생성 완료: " + authUrl.substring(0, Math.min(authUrl.length(), 100)) + "...");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "auth_url", authUrl));
        } catch (Exception e) {
            System.err.println("[Google Auth-URL] 예외 발생: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "error", "인증 URL 생성 중 오류 발생",
                    "message", e.getMessage()));
        }
    }

    /**
     * 구글 인증 콜백 처리 (POST)
     * 프론트엔드에서 POST 방식으로 code를 전달받아 처리
     * Authorization Code를 받아서 바로 토큰 교환 및 JWT 생성 후 프론트엔드로 리다이렉트 URL 반환
     */
    @PostMapping("/callback")
    public ResponseEntity<Map<String, Object>> googleCallbackPost(
            @RequestBody(required = false) Map<String, Object> requestBody) {

        System.out.println("=== 구글 콜백 POST 요청 수신 ===");

        String code = null;
        String error = null;
        String error_description = null;

        if (requestBody != null) {
            code = requestBody.containsKey("code") ? requestBody.get("code").toString() : null;
            error = requestBody.containsKey("error") ? requestBody.get("error").toString() : null;
            error_description = requestBody.containsKey("error_description")
                    ? requestBody.get("error_description").toString()
                    : null;
        }

        System.out.println("Code: " + code);
        System.out.println("Error: " + error);
        System.out.println("Error Description: " + error_description);
        System.out.println("============================");

        return processGoogleCallback(code, null, error, error_description);
    }

    /**
     * 구글 인증 콜백 처리 (GET)
     * OAuth 제공자로부터 직접 리다이렉트되는 경우 처리
     * Authorization Code를 받아서 바로 토큰 교환 및 JWT 생성 후 프론트엔드로 리다이렉트
     */
    @GetMapping("/callback")
    public RedirectView googleCallback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String error_description) {

        System.out.println("=== 구글 콜백 GET 요청 수신 ===");
        System.out.println("Code: " + code);
        System.out.println("State: " + state);
        System.out.println("Error: " + error);
        System.out.println("Error Description: " + error_description);
        System.out.println("============================");

        // 프론트엔드 도메인 (환경 변수에서 가져오거나 기본값 사용)
        String frontendUrl = System.getenv("FRONTEND_URL");
        if (frontendUrl == null || frontendUrl.isEmpty()) {
            // 프로덕션 환경 감지: Railway, AWS, 또는 프로덕션 프로파일
            String environment = System.getenv("SPRING_PROFILES_ACTIVE");
            String railwayEnv = System.getenv("RAILWAY_ENVIRONMENT");
            String awsRegion = System.getenv("AWS_REGION");

            if ((environment != null && (environment.contains("prod") || environment.contains("production")))
                    || railwayEnv != null
                    || awsRegion != null) {
                frontendUrl = "https://www.minsol.kr";
            } else {
                frontendUrl = "http://localhost:3000";
            }
        }
        // 프로토콜이 없으면 https:// 추가
        if (!frontendUrl.startsWith("http://") && !frontendUrl.startsWith("https://")) {
            frontendUrl = "https://" + frontendUrl;
        }

        if (code != null) {
            try {
                // 공통 처리 로직 호출
                ResponseEntity<Map<String, Object>> response = processGoogleCallback(code, state, error,
                        error_description);

                // ResponseEntity에서 redirectUrl 추출
                Map<String, Object> responseBody = response.getBody();
                if (responseBody != null && responseBody.containsKey("redirectUrl")) {
                    Object redirectUrlObj = responseBody.get("redirectUrl");
                    if (redirectUrlObj != null) {
                        return new RedirectView(redirectUrlObj.toString());
                    }
                }

                // redirectUrl이 없으면 기본 에러 처리 - URL에 에러 파라미터 포함하지 않음
                System.err.println("[Google Callback GET] 리다이렉트 URL을 생성할 수 없습니다.");
                return new RedirectView(frontendUrl);

            } catch (Exception e) {
                System.err.println("[Google Callback GET] 구글 인증 처리 중 오류 발생: " + e.getMessage());
                e.printStackTrace();
                // URL에 에러 파라미터 포함하지 않음
                return new RedirectView(frontendUrl);
            }
        } else if (error != null) {
            // OAuth 에러 - URL에 에러 파라미터 포함하지 않음
            System.err.println("[Google Callback GET] OAuth 에러: " + error);
            return new RedirectView(frontendUrl);
        } else {
            String redirectUrl = frontendUrl + "?error=" + URLEncoder.encode("인증 코드가 없습니다.", StandardCharsets.UTF_8);
            System.out.println("인증 코드 없음, 프론트엔드로 리다이렉트: " + redirectUrl);
            return new RedirectView(redirectUrl);
        }
    }

    /**
     * 구글 콜백 공통 처리 로직
     */
    private ResponseEntity<Map<String, Object>> processGoogleCallback(
            String code, String state, String error, String error_description) {

        // 프론트엔드 도메인 (환경 변수에서 가져오거나 기본값 사용)
        String frontendUrl = System.getenv("FRONTEND_URL");
        if (frontendUrl == null || frontendUrl.isEmpty()) {
            // 프로덕션 환경 감지: Railway, AWS, 또는 프로덕션 프로파일
            String environment = System.getenv("SPRING_PROFILES_ACTIVE");
            String railwayEnv = System.getenv("RAILWAY_ENVIRONMENT");
            String awsRegion = System.getenv("AWS_REGION");

            if ((environment != null && (environment.contains("prod") || environment.contains("production")))
                    || railwayEnv != null
                    || awsRegion != null) {
                frontendUrl = "https://www.minsol.kr";
            } else {
                frontendUrl = "http://localhost:3000";
            }
        }
        // 프로토콜이 없으면 https:// 추가
        if (!frontendUrl.startsWith("http://") && !frontendUrl.startsWith("https://")) {
            frontendUrl = "https://" + frontendUrl;
        }

        Map<String, Object> response = new HashMap<>();

        if (error != null) {
            // 에러 발생 - URL에 에러 파라미터 포함하지 않음
            response.put("success", false);
            response.put("error", error);
            response.put("error_description", error_description);
            response.put("redirectUrl", frontendUrl);
            System.err.println("[Google Callback] OAuth 에러 발생: " + error);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        if (code == null) {
            // 인증 코드가 없는 경우 - URL에 에러 파라미터 포함하지 않음
            response.put("success", false);
            response.put("message", "인증 코드가 없습니다.");
            response.put("redirectUrl", frontendUrl);
            System.err.println("[Google Callback] 인증 코드가 없습니다.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        try {
            // 1. Authorization Code를 Access Token으로 교환
            Map<String, Object> tokenResponse = googleOAuthService.getAccessToken(code);
            String googleAccessToken = (String) tokenResponse.get("access_token");
            String googleRefreshToken = (String) tokenResponse.get("refresh_token");
            Object expiresIn = tokenResponse.get("expires_in"); // 초 단위

            if (googleAccessToken == null) {
                throw new RuntimeException("구글 Access Token을 받을 수 없습니다.");
            }

            // 2. Access Token으로 사용자 정보 조회
            Map<String, Object> userInfo = googleOAuthService.getUserInfo(googleAccessToken);
            Map<String, Object> extractedUserInfo = googleOAuthService.extractUserInfo(userInfo);

            // 3. 사용자 ID 추출
            String userId = (String) extractedUserInfo.get("google_id");

            // 4. JWT 토큰 생성 (자체 JWT)
            String jwtAccessToken = jwtTokenProvider.generateAccessToken(userId, "google", extractedUserInfo);
            String jwtRefreshToken = jwtTokenProvider.generateRefreshToken(userId, "google");

            // 5. 모든 토큰을 Redis와 Neon에 저장 (통합 저장)
            long googleTokenExpireTime = expiresIn != null ? Long.parseLong(expiresIn.toString()) : 3600;
            long jwtAccessExpireTime = 3600; // 1시간 (초)
            long jwtRefreshExpireTime = 2592000; // 30일 (초)
            
            tokenService.saveAllTokens(
                "google", userId,
                googleAccessToken, googleRefreshToken,
                jwtAccessToken, jwtRefreshToken,
                googleTokenExpireTime, jwtAccessExpireTime, jwtRefreshExpireTime
            );

            // 7. 프론트엔드로 리다이렉트 URL 생성 (JWT 토큰 포함)
            String redirectUrl = frontendUrl + "/dashboard/google?token="
                    + URLEncoder.encode(jwtAccessToken, StandardCharsets.UTF_8);
            if (jwtRefreshToken != null) {
                redirectUrl += "&refresh_token=" + URLEncoder.encode(jwtRefreshToken, StandardCharsets.UTF_8);
            }

            System.out.println("JWT 토큰 생성 완료, 프론트엔드로 리다이렉트: " + redirectUrl);

            response.put("success", true);
            response.put("message", "구글 로그인 성공");
            response.put("token", jwtAccessToken);
            response.put("refresh_token", jwtRefreshToken);
            response.put("redirectUrl", redirectUrl);

            return ResponseEntity.status(HttpStatus.OK).body(response);

        } catch (Exception e) {
            System.err.println("[Google Callback] 구글 인증 처리 중 오류 발생: " + e.getMessage());
            e.printStackTrace();

            // 에러 발생 시 - URL에 에러 파라미터 포함하지 않음
            response.put("success", false);
            response.put("error", "인증 처리 중 오류가 발생했습니다.");
            response.put("message", e.getMessage());
            response.put("redirectUrl", frontendUrl);

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * 구글 로그인 요청 처리
     * Next.js에서 성공으로 인식하도록 항상 성공 응답 반환
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> googleLogin(
            @RequestBody(required = false) Map<String, Object> request,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest httpRequest) {
        System.out.println("😎😎😎😎=== 구글 로그인 요청 수신 ===");
        System.out.println("Request Body: " + request);

        // Authorization 헤더에서 토큰 확인
        if (authHeader != null) {
            System.out.println("Authorization 헤더: " + authHeader);
            if (authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                System.out.println("추출된 토큰: " + token.substring(0, Math.min(token.length(), 50)) + "...");
                // JWT 토큰 파싱 및 정보 출력
                System.out.println(JwtUtil.formatTokenInfo(authHeader));
            }
        } else {
            System.out.println("Authorization 헤더 없음");
        }

        System.out.println("============================");

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "구글 로그인이 성공적으로 처리되었습니다.");
        response.put("token", "mock_token_" + System.currentTimeMillis());

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * 구글 토큰 검증 및 저장
     * Authorization Code를 Access Token으로 교환하고 Redis에 저장
     */
    @PostMapping("/token")
    public ResponseEntity<Map<String, Object>> googleToken(@RequestBody(required = false) Map<String, Object> request) {
        System.out.println("=== 구글 토큰 요청 수신 ===");
        System.out.println("Request Body: " + request);
        System.out.println("============================");

        Map<String, Object> response = new HashMap<>();

        if (request == null || !request.containsKey("code")) {
            response.put("success", false);
            response.put("message", "Authorization Code가 필요합니다.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        String code = request.get("code").toString();
        String requestState = request.containsKey("state") ? request.get("state").toString() : null;

        // Redis에서 Authorization Code 검증
        String savedState = tokenService.verifyAndDeleteAuthorizationCode("google", code);
        if (savedState == null) {
            response.put("success", false);
            response.put("message", "유효하지 않거나 만료된 Authorization Code입니다.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // State 검증 (있는 경우)
        if (requestState != null && !requestState.equals(savedState)) {
            response.put("success", false);
            response.put("message", "State 값이 일치하지 않습니다.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // TODO: 실제 구글 OAuth2 API를 호출하여 Access Token 교환
        // 현재는 Mock 응답
        String accessToken = "mock_access_token_" + System.currentTimeMillis();
        String refreshToken = "mock_refresh_token_" + System.currentTimeMillis();
        String userId = "mock_google_user_id"; // 실제로는 구글 API에서 받아온 사용자 ID

        // Redis에 토큰 저장 (Access Token: 1시간, Refresh Token: 30일)
        tokenService.saveAccessToken("google", userId, accessToken, 3600);
        tokenService.saveRefreshToken("google", userId, refreshToken, 2592000);

        response.put("success", true);
        response.put("message", "구글 토큰이 성공적으로 처리되었습니다.");
        response.put("access_token", accessToken);
        response.put("refresh_token", refreshToken);
        response.put("user_id", userId);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * 구글 사용자 정보 조회
     * Next.js에서 성공으로 인식하도록 항상 성공 응답 반환
     */
    @GetMapping("/user")
    public ResponseEntity<Map<String, Object>> googleUserInfo(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            HttpServletRequest request) {
        System.out.println("=== 구글 사용자 정보 조회 요청 수신 ===");

        // Authorization 헤더에서 토큰 출력 및 JWT 파싱
        if (authHeader != null) {
            System.out.println("Authorization 헤더: " + authHeader);
            if (authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                System.out.println("추출된 토큰: " + token.substring(0, Math.min(token.length(), 50)) + "...");

                // JWT 토큰 파싱 및 정보 출력
                System.out.println(JwtUtil.formatTokenInfo(authHeader));
            }
        } else {
            System.out.println("Authorization 헤더 없음");
        }

        System.out.println("============================");

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "구글 사용자 정보를 성공적으로 조회했습니다.");

        Map<String, Object> userInfo = new HashMap<>();
        userInfo.put("id", "mock_google_user_id");
        userInfo.put("nickname", "구글 사용자");
        userInfo.put("email", "google@example.com");

        response.put("user", userInfo);

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    /**
     * 모든 구글 관련 요청에 대한 기본 핸들러
     * Next.js에서 성공으로 인식하도록 항상 성공 응답 반환
     */
    @RequestMapping(value = "/**", method = { RequestMethod.GET, RequestMethod.POST, RequestMethod.PUT,
            RequestMethod.DELETE })
    public ResponseEntity<Map<String, Object>> googleDefault() {
        System.out.println("=== 구글 기본 핸들러 요청 수신 ===");
        System.out.println("============================");

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "구글 요청이 성공적으로 처리되었습니다.");

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}