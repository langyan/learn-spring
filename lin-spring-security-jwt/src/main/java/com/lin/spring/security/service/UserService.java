package com.lin.spring.security.service;

import com.lin.spring.security.dto.UserInfoResponse;
import com.lin.spring.security.entity.Permission;
import com.lin.spring.security.entity.Role;
import com.lin.spring.security.entity.User;
import com.lin.spring.security.repository.UserRepository;
import com.lin.spring.security.security.AuthorityResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuthorityResolver authorityResolver;

    @Transactional(readOnly = true)
    public UserInfoResponse getUserInfo(String username) {
        return userRepository.findByUsernameWithAuthorities(username)
                .map(UserService::toUserInfo)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));
    }

    @Transactional(readOnly = true)
    public List<UserInfoResponse> listUsers() {
        return userRepository.findAllWithAuthorities().stream()
                .map(UserService::toUserInfo)
                .toList();
    }

    @Transactional
    public void deleteUser(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + id));
        String username = user.getUsername();
        userRepository.delete(user);
        // dropped user must not keep serving cached authorities
        authorityResolver.evict(username);
    }

    private static UserInfoResponse toUserInfo(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
        Set<String> permissions = user.getRoles().stream()
                .flatMap(role -> role.getPermissions().stream())
                .map(Permission::getName)
                .collect(Collectors.toSet());
        return new UserInfoResponse(user.getId(), user.getUsername(), roles, permissions);
    }
}
