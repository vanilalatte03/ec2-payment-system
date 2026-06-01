package com.teamec2.paymentsystem.domain.auth.service;

import com.teamec2.paymentsystem.domain.auth.dto.LoginRequest;
import com.teamec2.paymentsystem.domain.auth.dto.LoginResponse;
import com.teamec2.paymentsystem.domain.auth.dto.LogoutResponse;
import com.teamec2.paymentsystem.domain.auth.dto.SignupRequest;
import com.teamec2.paymentsystem.domain.auth.dto.SignupResponse;
import com.teamec2.paymentsystem.domain.user.entity.User;
import com.teamec2.paymentsystem.domain.user.repository.UserRepository;
import com.teamec2.paymentsystem.global.exception.BusinessException;
import com.teamec2.paymentsystem.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
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

    private BusinessException invalidLoginCredentials() {
        return new BusinessException(ErrorCode.INVALID_LOGIN_CREDENTIALS);
    }
}
