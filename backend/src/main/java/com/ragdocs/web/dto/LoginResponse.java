package com.ragdocs.web.dto;

public record LoginResponse(String token, UserDto user) {
}
