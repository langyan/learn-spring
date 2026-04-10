package com.lin.spring.apiprotect.repository;

import com.lin.spring.apiprotect.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    Page<User> findByEmailContainingIgnoreCase(String email, Pageable pageable);

    Page<User> findByEmailContainingIgnoreCaseAndRoleIgnoreCase(String email, String role, Pageable pageable);
}
