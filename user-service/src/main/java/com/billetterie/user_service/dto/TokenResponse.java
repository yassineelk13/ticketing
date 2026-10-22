package com.billetterie.user_service.dto;

public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
}