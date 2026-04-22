package com.lin.spring.sharding.repository;

import com.lin.spring.sharding.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 用户仓储；实际访问的库由当前线程 {@link com.lin.spring.sharding.routing.ShardContext} 决定。
 */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {}
