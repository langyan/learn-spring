package com.lin.spring.sharding.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建用户请求体。
 */
@Data
public class CreateUserRequest {

    @NotBlank
    @Email
    private String email;
}
