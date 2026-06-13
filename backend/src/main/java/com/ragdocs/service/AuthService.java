package com.ragdocs.service;

import com.ragdocs.auth.CurrentUser;
import com.ragdocs.auth.JwtService;
import com.ragdocs.common.BusinessException;
import com.ragdocs.common.ErrorCode;
import com.ragdocs.domain.User;
import com.ragdocs.repository.UserRepository;
import com.ragdocs.web.dto.LoginRequest;
import com.ragdocs.web.dto.LoginResponse;
import com.ragdocs.web.dto.UserDto;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED, "用户名或密码错误"));
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED, "用户名或密码错误");
        }
        CurrentUser currentUser = new CurrentUser(user.id(), user.username(), user.role());
        return new LoginResponse(jwtService.issue(currentUser), new UserDto(user.id(), user.username(), user.role()));
    }
}
