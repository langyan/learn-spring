package com.lin.spring.sharding.service;

import com.lin.spring.sharding.annotation.ShardRoutedRead;
import com.lin.spring.sharding.annotation.Sharded;
import com.lin.spring.sharding.dto.CreateUserRequest;
import com.lin.spring.sharding.dto.UserResponse;
import com.lin.spring.sharding.entity.AppUser;
import com.lin.spring.sharding.id.SnowflakeIdGenerator;
import com.lin.spring.sharding.repository.AppUserRepository;
import com.lin.spring.sharding.routing.ShardContext;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户写读服务：写按业务键哈希路由，读按雪花 ID 解析分片。
 */
@Service
public class AppUserService {

    private final AppUserRepository repository;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    public AppUserService(AppUserRepository repository, SnowflakeIdGenerator snowflakeIdGenerator) {
        this.repository = repository;
        this.snowflakeIdGenerator = snowflakeIdGenerator;
    }

    /**
     * 创建用户：一致哈希选分片，雪花 ID 写入主键与冗余 shardId。
     */
    @Sharded(key = "#request.email")
    @Transactional
    public UserResponse create(CreateUserRequest request) {
        Integer shard = ShardContext.get();
        if (shard == null) {
            throw new IllegalStateException("分片上下文缺失");
        }
        long id = snowflakeIdGenerator.nextId(shard);
        AppUser user = new AppUser(id, request.getEmail(), shard);
        return UserResponse.from(repository.save(user));
    }

    /**
     * 按主键查询：从 ID 解析分片后单分片点查。
     */
    @ShardRoutedRead(idParam = "id")
    @Transactional(readOnly = true)
    public Optional<UserResponse> findById(Long id) {
        return repository.findById(id).map(UserResponse::from);
    }
}
