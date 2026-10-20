package com.lin.spring.security.repository;

import com.lin.spring.security.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface RoleRepository extends JpaRepository<Role, Long> {

    @Query("""
            SELECT DISTINCT r FROM Role r
            LEFT JOIN FETCH r.permissions
            WHERE r.name = :name
            """)
    Optional<Role> findByNameWithPermissions(@Param("name") String name);

    Optional<Role> findByName(String name);
}
