package com.lin.spring.chat.service.common;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** 从 SecurityContext 取当前登录用户 id（JwtAuthFilter 放入的 principal） */
public final class CurrentUser {

    private CurrentUser() {
    }

    public static String id() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null || !auth.isAuthenticated()) {
            throw ApiException.unauthorized("未登录");
        }
        return auth.getPrincipal().toString();
    }
}
