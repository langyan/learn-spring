package com.lin.spring.shiro.repository;

import com.lin.spring.shiro.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Loads a user together with roles and their permissions in a single query
     * (JOIN FETCH avoids the N+1 problem when assembling authorities).
     */
    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN FETCH u.roles r
            LEFT JOIN FETCH r.permissions
            WHERE u.username = :username
            """)
    Optional<User> findByUsernameWithAuthorities(@Param("username") String username);

    @Query("""
            SELECT DISTINCT u FROM User u
            LEFT JOIN FETCH u.roles r
            LEFT JOIN FETCH r.permissions
            """)
    List<User> findAllWithAuthorities();

    boolean existsByUsername(String username);
}
