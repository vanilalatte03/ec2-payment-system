package com.teamec2.paymentsystem.domain.auth.controller;

import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    private static final int BODY_STATUS = 200;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    UserRepository userRepository;

    @Autowired
    PasswordEncoder passwordEncoder;

    @Test
    void 회원가입_성공하면_회원정보를반환한다() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password123!",
                                  "name": "홍길동",
                                  "phone": "010-1234-5678"
                                }
                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.data.phone").value("010-1234-5678"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    @Test
    void 회원가입_성공하면_비밀번호를BCrypt해시로저장한다() throws Exception {
        String email = uniqueEmail();
        String rawPassword = "Password123!";

        signup(email, rawPassword);

        User user = userRepository.findByEmail(email)
                .orElseThrow();

        org.assertj.core.api.Assertions.assertThat(user.getPassword()).isNotEqualTo(rawPassword);
        org.assertj.core.api.Assertions.assertThat(passwordEncoder.matches(rawPassword, user.getPassword())).isTrue();
    }

    @Test
    void 회원가입_중복이메일이면_EMAIL_ALREADY_EXISTS를반환한다() throws Exception {
        String email = uniqueEmail();
        signup(email, "Password123!");

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password123!",
                                  "name": "홍길동",
                                  "phone": "010-1234-5678"
                                }
                """.formatted(email)))
                .andExpect(status().is(ErrorCode.EMAIL_ALREADY_EXISTS.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.EMAIL_ALREADY_EXISTS.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.EMAIL_ALREADY_EXISTS.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 회원가입_요청값이올바르지않으면_400을반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": "weak",
                                  "name": "",
                                  "phone": ""
                                }
                """))
                .andExpect(status().is(ErrorCode.VALIDATION_FAILED.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void 회원가입_필수값이비어있으면_400을반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "",
                                  "password": "",
                                  "name": "",
                                  "phone": ""
                                }
                """))
                .andExpect(status().is(ErrorCode.VALIDATION_FAILED.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void 로그인_성공하면_사용자요약정보를반환한다() throws Exception {
        String email = uniqueEmail();
        signup(email, "Password123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password123!"
                                }
                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.userId").isNumber())
                .andExpect(jsonPath("$.data.email").value(email))
                .andExpect(jsonPath("$.data.name").value("홍길동"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist());
    }

    @Test
    void 로그인_존재하지않는이메일이면_401을반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "unknown@example.com",
                                  "password": "wrong-password"
                                }
                """))
                .andExpect(status().is(ErrorCode.INVALID_LOGIN_CREDENTIALS.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_LOGIN_CREDENTIALS.name()))
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_LOGIN_CREDENTIALS.getMessage()))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void 로그인_비밀번호가틀리면_401을반환한다() throws Exception {
        String email = uniqueEmail();
        signup(email, "Password123!");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "WrongPassword123!"
                                }
                """.formatted(email)))
                .andExpect(status().is(ErrorCode.INVALID_LOGIN_CREDENTIALS.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.INVALID_LOGIN_CREDENTIALS.name()))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.accessToken").doesNotExist());
    }

    @Test
    void 로그인_요청값이올바르지않으면_400을반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "invalid-email",
                                  "password": ""
                                }
                """))
                .andExpect(status().is(ErrorCode.VALIDATION_FAILED.getHttpStatus().value()))
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.code").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void 로그아웃_성공하면_로그아웃여부를반환한다() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(BODY_STATUS))
                .andExpect(jsonPath("$.message").value("요청이 성공했습니다."))
                .andExpect(jsonPath("$.data.loggedOut").value(true));
    }

    private void signup(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "email": "%s",
                          "password": "%s",
                          "name": "홍길동",
                          "phone": "010-1234-5678"
                        }
                        """.formatted(email, password)))
                .andExpect(status().isCreated());
    }

    private String uniqueEmail() {
        return UUID.randomUUID() + "@example.com";
    }
}
