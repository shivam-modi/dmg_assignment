package com.dmg.moviebooking.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthDtos {

    public record RegisterRequest(
            @NotBlank String name,
            @Email @NotBlank String email,
            @Size(min = 8, message = "Password must be at least 8 characters") String password
    ) {
    }

    public record LoginRequest(@Email @NotBlank String email, @NotBlank String password) {
    }

    public record AuthResponse(String token, String email, String role) {
    }
}
