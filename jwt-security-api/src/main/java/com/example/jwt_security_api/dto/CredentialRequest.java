package com.example.jwt_security_api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CredentialRequest(
        @NotBlank
        @Size(max = 100)
        @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._ -]*")
        String provider,
        @NotBlank
        @Size(max = 700)
        String secret) {
}
