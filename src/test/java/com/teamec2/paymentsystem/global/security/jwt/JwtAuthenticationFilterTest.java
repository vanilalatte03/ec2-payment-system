package com.teamec2.paymentsystem.global.security.jwt;

import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.global.security.SecurityErrorResponseWriter;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    JwtTokenProvider jwtTokenProvider;

    @Mock
    SecurityErrorResponseWriter securityErrorResponseWriter;

    @Mock
    FilterChain filterChain;

    JwtAuthenticationFilter jwtAuthenticationFilter;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        jwtAuthenticationFilter = new JwtAuthenticationFilter(jwtTokenProvider, securityErrorResponseWriter);
    }

    @Test
    void 공개엔드포인트_잘못된토큰이어도_토큰을파싱하지않고필터를통과한다() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer invalid-token");

        // when
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // then
        verifyNoInteractions(jwtTokenProvider, securityErrorResponseWriter);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void 보호엔드포인트_잘못된토큰이면_INVALID_TOKEN응답을쓰고필터체인을중단한다() throws Exception {
        // given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/points/balance");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("Authorization", "Bearer invalid-token");
        when(jwtTokenProvider.parseClaims("invalid-token")).thenThrow(new JwtException("invalid token"));

        // when
        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // then
        verify(securityErrorResponseWriter).write(response, ErrorCode.INVALID_TOKEN);
        verify(filterChain, never()).doFilter(request, response);
    }
}
