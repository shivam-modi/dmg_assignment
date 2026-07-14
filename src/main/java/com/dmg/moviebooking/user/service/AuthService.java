package com.dmg.moviebooking.user.service;

import com.dmg.moviebooking.common.exception.BusinessRuleViolationException;
import com.dmg.moviebooking.security.JwtService;
import com.dmg.moviebooking.user.dto.AuthDtos.AuthResponse;
import com.dmg.moviebooking.user.dto.AuthDtos.LoginRequest;
import com.dmg.moviebooking.user.dto.AuthDtos.RegisterRequest;
import com.dmg.moviebooking.user.entity.Role;
import com.dmg.moviebooking.user.entity.User;
import com.dmg.moviebooking.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    /** Public signup always creates a CUSTOMER — there is no self-service path to ADMIN (see AdminSeeder). */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleViolationException("An account with this email already exists");
        }
        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(Role.CUSTOMER)
                .createdAt(Instant.now())
                .build();
        user = userRepository.save(user);
        return new AuthResponse(jwtService.issueToken(user), user.getEmail(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password()));
        } catch (org.springframework.security.core.AuthenticationException ex) {
            throw new BadCredentialsException("Invalid email or password");
        }
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
        return new AuthResponse(jwtService.issueToken(user), user.getEmail(), user.getRole().name());
    }
}
