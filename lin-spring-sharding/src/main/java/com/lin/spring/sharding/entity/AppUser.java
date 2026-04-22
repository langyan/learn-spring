package com.lin.spring.sharding.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 分片用户实体；主键由应用侧雪花生成，{@code shardId} 冗余存储便于排障。
 */
@Entity
@Table(name = "app_user")
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    @Id
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "shard_id", nullable = false)
    private Integer shardId;

    public AppUser(Long id, String email, Integer shardId) {
        this.id = id;
        this.email = email;
        this.shardId = shardId;
    }
}
