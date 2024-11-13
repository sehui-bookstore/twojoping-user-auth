package com.nhnacademy.twojoping.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nhnacademy.twojoping.dto.response.MemberInfoResponseDto;
import com.nhnacademy.twojoping.exception.InvalidRefreshToken;
import com.nhnacademy.twojoping.security.provider.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        try {
            String jti = jwtTokenProvider.getJti(accessToken);
            Map<Object, Object> map = redisTemplate.opsForHash().entries(jti);

            long key = 0;
            String value = null;
            for (Map.Entry<Object, Object> entry : map.entrySet()) {
                key = Long.parseLong(entry.getKey().toString());
                value = entry.getValue().toString();
            }
            MemberInfoResponseDto memberInfoResponseDto = new MemberInfoResponseDto(key, value);
            return ResponseEntity.ok(memberInfoResponseDto);
        } catch (Exception e) {
            throw new InvalidRefreshToken();
        }
    }

    @GetMapping("/refreshToken")
    public ResponseEntity<?> refreshAccessToken(@CookieValue(name = "refreshToken") String refreshToken, HttpServletResponse response) {
        // refreshToken 쿠키 검증후 accessToken 쿠키 재발급, invalid token 이면 예외처리
        if (jwtTokenProvider.validateToken(refreshToken) && refreshToken != null) {
            String newAccessToken = jwtTokenProvider.regenerateAccessToken(refreshToken);
            Cookie cookie = new Cookie("accessToken", newAccessToken);
            cookie.setHttpOnly(true);
            cookie.setSecure(false);
            cookie.setPath("/");
            response.addCookie(cookie);
            return ResponseEntity.ok().build();
        }
        throw new InvalidRefreshToken();
    }

}
