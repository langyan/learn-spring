package com.lin.spring.shiro.service;

import com.lin.spring.shiro.auth.AuthorityResolver;
import com.lin.spring.shiro.auth.UserAuthService;
import com.lin.spring.shiro.dto.UserInfoResponse;
import com.lin.spring.shiro.entity.User;
import com.lin.spring.shiro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.apache.shiro.authc.UnknownAccountException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserAuthService userAuthService;
    private final AuthorityResolver authorityResolver;

    @Transactional(readOnly = true)
    public UserInfoResponse getCurrentUser(String username) {
        return userAuthService.getUserInfo(username);
    }

    @Transactional(readOnly = true)
    public List<UserInfoResponse> listUsers() {
        return userRepository.findAllWithAuthorities().stream()
                .map(user -> {
                    var roles = new java.util.HashSet<String>();
                    var permissions = new java.util.HashSet<String>();
                    user.getRoles().forEach(role -> {
                        roles.add(role.getName());
                        role.getPermissions().forEach(p -> permissions.add(p.getName()));
                    });
                    return new UserInfoResponse(user.getId(), user.getUsername(), roles, permissions);
                })
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

    @Transactional(readOnly = true)
    public User getByUsername(String username) {
        return userRepository.findByUsernameWithAuthorities(username)
                .orElseThrow(() -> new UnknownAccountException("Unknown account"));
    }
}
