package com.teamec2.paymentsystem.domain.auth.service;

import com.teamec2.paymentsystem.domain.auth.dto.LoginRequest;
import com.teamec2.paymentsystem.domain.auth.dto.LoginResponse;
import com.teamec2.paymentsystem.domain.auth.dto.LogoutResponse;
import com.teamec2.paymentsystem.domain.auth.dto.SignupRequest;
import com.teamec2.paymentsystem.domain.auth.dto.SignupResponse;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 가입된 이메일입니다.");
        }

        User user = User.create(
                request.email(),
                passwordEncoder.encode(request.password()),
                request.name(),
                request.phone()
        );
        User savedUser = userRepository.save(user);

        return new SignupResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getName(),
                savedUser.getPhone(),
                savedUser.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(this::invalidLoginCredentials);

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw invalidLoginCredentials();
        }

        return new LoginResponse(user.getId(), user.getEmail(), user.getName());
    }

    public LogoutResponse logout() {
        return new LogoutResponse(true);
    }

    private ResponseStatusException invalidLoginCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}
