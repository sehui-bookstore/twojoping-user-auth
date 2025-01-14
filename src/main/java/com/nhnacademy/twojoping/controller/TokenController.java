package com.nhnacademy.twojoping.controller;

import com.nhnacademy.twojoping.dto.response.MemberInfoResponseDto;
import com.nhnacademy.twojoping.exception.InvalidRefreshToken;
import com.nhnacademy.twojoping.security.provider.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class TokenController {

    private final RedisTemplate<String, Object> redisTemplate;

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * @author Sauter001
     * @param accessToken 사용자 정보를 가져올 JWT access token
     * @return 사용자의 로그인 id와 role
     */
    @GetMapping("/user-info")
    public ResponseEntity<MemberInfoResponseDto> getUserInfo(@CookieValue(name = "accessToken") String accessToken) {
        // jti 값을 이용해서 redis 에서 정보를 조회함
        String jti = jwtTokenProvider.getJti(accessToken);
        Map<Object, Object> map = redisTemplate.opsForHash().entries(jti);

        // response
        String nickName = null;
        long key = 0;
        String role = null;
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            if (entry.getKey().toString().equals("0")) {
                nickName = (String) entry.getValue();
            }
            key = Long.parseLong(entry.getKey().toString());
            role = entry.getValue().toString();
        }
        MemberInfoResponseDto memberInfoResponseDto = new MemberInfoResponseDto(key, nickName, role);
        return ResponseEntity.ok(memberInfoResponseDto);
    }

    @GetMapping("/refreshToken")
    public ResponseEntity<?> refreshAccessToken(@CookieValue(name = "refreshToken") String refreshToken,
//                                                @RequestHeader("X-Customer-Id") String customerId,
//                                                @RequestHeader("X-Customer-Role") String customerRole,
                                                HttpServletResponse response) {
        // 이전 토큰 삭제
        // jti 값을 이용해서 redis 에서 정보를 조회함
        String previousJti = jwtTokenProvider.getJti(refreshToken);
        Map<Object, Object> map = redisTemplate.opsForHash().entries(previousJti);
        redisTemplate.delete(previousJti);

        // 새로운 Jti
        String newJti = "";

        // refreshToken 쿠키 검증후 accessToken 쿠키 재발급, invalid token 이면 예외처리
        if (jwtTokenProvider.validateToken(refreshToken) && refreshToken != null) {
            String newAccessToken = jwtTokenProvider.generateAccessToken();
            newJti = jwtTokenProvider.getJti(newAccessToken);
            String newRefreshToken = jwtTokenProvider.reGenerateRefreshToken(
                    newJti,
                    jwtTokenProvider.getRemainingExpirationTime(refreshToken));

            // accessToken 재발급
            Cookie accessCookie = new Cookie("accessToken", newAccessToken);
            accessCookie.setHttpOnly(true);
            accessCookie.setSecure(false);
            accessCookie.setPath("/");
            response.addCookie(accessCookie);

            // refreshToken 재발급
            Cookie refreshCookie = new Cookie("refreshToken", newRefreshToken);
            refreshCookie.setHttpOnly(true);
            refreshCookie.setSecure(false);
            refreshCookie.setPath("/");
            response.addCookie(refreshCookie);

            redisTemplate.opsForHash().putAll(newJti, map);

            return ResponseEntity.ok().build();
        }
        throw new InvalidRefreshToken();
    }

}
