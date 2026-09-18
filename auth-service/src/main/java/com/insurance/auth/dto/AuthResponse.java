package com.insurance.auth.dto;

/**
 * @param accessToken      short-lived JWT sent as {@code Authorization: Bearer ...}
 * @param refreshToken     opaque long-lived token used only against {@code POST /api/auth/refresh}
 * @param tokenType        always "Bearer"
 * @param expiresInSeconds access token lifetime
 */
public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds,
                           UserResponse user) {

    public static AuthResponse of(String accessToken, String refreshToken, long expiresInSeconds, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresInSeconds, user);
    }
}
