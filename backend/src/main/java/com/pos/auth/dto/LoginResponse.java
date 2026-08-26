package com.pos.auth.dto;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        boolean passwordChangeRequired
) {}
