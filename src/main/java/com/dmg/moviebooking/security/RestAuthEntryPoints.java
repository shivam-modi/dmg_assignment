package com.dmg.moviebooking.security;

import com.dmg.moviebooking.common.ApiError;
import tools.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Rejections at the security-filter-chain level (before the request reaches a controller) bypass
 * GlobalExceptionHandler entirely — Spring Security resolves them via these two interfaces
 * directly. Wired here to keep the JSON error shape consistent with the rest of the API, and to
 * make missing-credentials (401) and insufficient-role (403) actually distinguishable, which
 * requires anonymous authentication to be disabled in SecurityConfig.
 */
@Component
@RequiredArgsConstructor
public class RestAuthEntryPoints implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, "Authentication required", request.getRequestURI());
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, "You do not have permission to perform this action", request.getRequestURI());
    }

    private void write(HttpServletResponse response, HttpStatus status, String message, String path) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ApiError error = ApiError.of(status.value(), status.getReasonPhrase(), message, path);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
