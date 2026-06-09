package com.teamec2.paymentsystem.domain.point.controller;

import com.teamec2.paymentsystem.domain.point.entity.PointTransaction;
import com.teamec2.paymentsystem.domain.point.enums.PointTransactionType;
import com.teamec2.paymentsystem.domain.point.repository.PointTransactionRepository;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import com.teamec2.paymentsystem.global.security.jwt.JwtTokenProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PointControllerTest {

    private static final int BODY_STATUS = 200;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PointTransactionRepository pointTransactionRepository;

    @Autowired
    JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    void setUp() {
        clearDatabase();
    }

    @AfterEach
    void tearDown() {
        clearDatabase();
    }

    @Test
    void 포인트잔액조회_성공하면_현재회원잔액을반환한다() throws Exception {
        // given
        User user = 회원_저장();

        // when
        // then
        mockMvc.perform(get("/api/points/balance")
                        .header("Authorization", "Bearer " + accessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.balance").value(0L));
    }

    @Test
    void 포인트잔액조회_토큰이없으면_UNAUTHORIZED를반환한다() throws Exception {
        // when
        // then
        mockMvc.perform(get("/api/points/balance"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHORIZED.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 포인트거래내역조회_타입과페이지조건으로_목록을반환한다() throws Exception {
        // given
        User user = 회원_저장();
        pointTransactionRepository.save(PointTransaction.createForSignupBonus(user, 1_000L));

        // when
        // then
        mockMvc.perform(get("/api/points/transactions")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .param("type", PointTransactionType.SIGNUP_BONUS.name())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].type").value(PointTransactionType.SIGNUP_BONUS.name()))
                .andExpect(jsonPath("$.data.content[0].amount").value(1_000L))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1L))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.hasNext").value(false));
    }

    @Test
    void 포인트거래내역조회_잘못된거래타입이면_INVALID_ENUM_VALUE를반환한다() throws Exception {
        // given
        User user = 회원_저장();

        // when
        // then
        mockMvc.perform(get("/api/points/transactions")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .param("type", "UNKNOWN_TYPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_ENUM_VALUE.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_ENUM_VALUE.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 포인트거래내역조회_페이지번호가음수이면_INVALID_PAGINATION을반환한다() throws Exception {
        // given
        User user = 회원_저장();

        // when
        // then
        mockMvc.perform(get("/api/points/transactions")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .param("page", "-1")
                        .param("size", "10"))
                .andExpect(status().is(ErrorCode.INVALID_PAGINATION.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAGINATION.name()))
                .andExpect(jsonPath("$.message").value("잘못된 페이지 번호입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 포인트거래내역조회_페이지크기가100을초과하면_INVALID_PAGINATION을반환한다() throws Exception {
        // given
        User user = 회원_저장();

        // when
        // then
        mockMvc.perform(get("/api/points/transactions")
                        .header("Authorization", "Bearer " + accessToken(user))
                        .param("page", "0")
                        .param("size", "101"))
                .andExpect(status().is(ErrorCode.INVALID_PAGINATION.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_PAGINATION.name()))
                .andExpect(jsonPath("$.message").value("잘못된 페이지 크기입니다."))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    private User 회원_저장() {
        return userRepository.save(User.create(
                uniqueEmail(),
                "Password123!",
                "홍길동",
                "010-1234-5678"
        ));
    }

    private String accessToken(User user) {
        return jwtTokenProvider.createAccessToken(user.getId());
    }

    private void clearDatabase() {
        pointTransactionRepository.deleteAll();
        userRepository.deleteAll();
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
