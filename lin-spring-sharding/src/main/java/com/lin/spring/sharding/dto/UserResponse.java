package com.lin.spring.sharding.dto;

import com.lin.spring.sharding.entity.AppUser;
import lombok.Value;

/**
 * 用户 API 响应。
 */
@Value
public class UserResponse {

    Long id;
    String email;
    Integer shardId;

    /**
     * 从实体构造响应。
     */
    public static UserResponse from(AppUser u) {
        return new UserResponse(u.getId(), u.getEmail(), u.getShardId());
    }
}
