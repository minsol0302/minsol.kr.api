package kr.minsol.api.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
// 로그확인용 주석 추가 2번째째
@RestController
@RequestMapping("/api/gateway")
public class GatewayController {

    /**
     * Gateway 연결 상태 확인
     * 
     * @return Gateway 상태 정보
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getGatewayStatus() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Gateway is running");
        response.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        response.put("service", "api.minsol.kr");

        return ResponseEntity.status(HttpStatus.OK).body(response);
    }
}
