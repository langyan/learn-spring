package com.lin.spring.shiro.dto;

import java.util.Set;

public record UserInfoResponse(
        Long id,
        String username,
        Set<String> roles,
        Set<String> permissions
) {
}
