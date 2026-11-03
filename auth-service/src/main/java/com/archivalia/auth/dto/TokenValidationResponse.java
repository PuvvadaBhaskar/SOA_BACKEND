package com.archivalia.auth.dto;

public record TokenValidationResponse(
        boolean valid,
        Long userId,
        String username,
        String role,
        String issuer,
        String expiresAt) {
}
