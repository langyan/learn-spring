package com.lin.spring.chat.service.repository;

import com.lin.spring.chat.service.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsername(String username);

    List<User> findTop20ByUsernameContainingOrNicknameContainingOrderByUsername(String username, String nickname);

    @Query("select u from User u where u.id in :ids")
    List<User> findAllByIdIn(@Param("ids") List<String> ids);
}
