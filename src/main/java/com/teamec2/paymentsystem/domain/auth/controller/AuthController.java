package com.teamec2.paymentsystem.domain.auth.controller;

import com.teamec2.paymentsystem.domain.auth.dto.LoginRequest;
import com.teamec2.paymentsystem.domain.auth.dto.LoginResponse;
import com.teamec2.paymentsystem.domain.auth.dto.LogoutResponse;
import com.teamec2.paymentsystem.domain.auth.dto.SignupRequest;
import com.teamec2.paymentsystem.domain.auth.dto.SignupResponse;
import com.teamec2.paymentsystem.domain.auth.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = authService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<LogoutResponse> logout() {
        LogoutResponse response = authService.logout();
        return ResponseEntity.ok(response);
    }
}
