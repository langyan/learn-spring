package com.lin.spring.validation.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class UserRegistrationRequest {
    @NotBlank(message = "Username cannot be blank")
    @Size(min = 4, max = 20, message = "Username must be between 4 and 20 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Username can only contain letters, numbers, and underscores")
    private String username;
    
    @NotBlank(message = "Email cannot be blank")
    @Email(message = "Email should be valid")
    private String email;
    
    @NotBlank(message = "Password cannot be blank")
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Pattern(
        regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=])(?=\\S+$).{8,}$",
        message = "Password must contain at least one digit, one lowercase, one uppercase letter, one special character and no whitespace"
    )
    private String password;
    
    @Min(value = 18, message = "You must be at least 18 years old")
    @Max(value = 120, message = "Age must be realistic")
    private int age;
    
    @AssertTrue(message = "You must agree to terms and conditions")
    private boolean agreedToTerms;
}
