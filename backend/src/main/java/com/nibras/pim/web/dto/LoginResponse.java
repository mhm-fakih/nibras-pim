package com.nibras.pim.web.dto;

public record LoginResponse(
        String token,
        AuthUserDto user) {
}
