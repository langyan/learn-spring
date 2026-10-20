package com.lin.spring.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(

        @NotBlank(message = "username must not be blank")
        @Size(min = 3, max = 50, message = "username must be 3-50 characters")
        String username,

        @NotBlank(message = "password must not be blank")
        @Size(min = 6, max = 100, message = "password must be 6-100 characters")
        String password
) {
}
