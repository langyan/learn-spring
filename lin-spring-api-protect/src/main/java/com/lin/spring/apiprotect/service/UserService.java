package com.lin.spring.apiprotect.service;

import com.lin.spring.apiprotect.dto.AdminUserResponse;
import com.lin.spring.apiprotect.dto.ProfileResponse;
import com.lin.spring.apiprotect.dto.ProfileUpdateRequest;
import com.lin.spring.apiprotect.model.User;
import com.lin.spring.apiprotect.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
public class UserService implements UserDetailsService {

    private static final Map<String, String> ALLOWED_SORTS = Map.of(
            "username", "username",
            "email", "email",
            "role", "role",
            "displayName", "displayName"
    );

    private final UserRepository userRepository;
    private final HtmlSanitizerService sanitizerService;

    public UserService(UserRepository userRepository, HtmlSanitizerService sanitizerService) {
        this.userRepository = userRepository;
        this.sanitizerService = sanitizerService;
    }

    @Override
    public User loadUserByUsername(String username) {
        return userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new EntityNotFoundException("User not found"));
    }

    @Transactional(readOnly = true)
    public ProfileResponse getCurrentProfile(String username) {
        User user = loadUserByUsername(username);
        return toProfileResponse(user);
    }

    @Transactional
    public ProfileResponse updateProfile(String username, ProfileUpdateRequest request) {
        User user = loadUserByUsername(username);
        user.setDisplayName(sanitizerService.sanitize(request.getDisplayName()));
        user.setBio(sanitizerService.sanitize(request.getBio()));
        return toProfileResponse(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> searchUsers(String email, String role, String sortBy, String direction, int page, int size) {
        String safeSort = ALLOWED_SORTS.getOrDefault(sortBy, "username");
        Sort sort = "desc".equalsIgnoreCase(direction)
                ? Sort.by(safeSort).descending()
                : Sort.by(safeSort).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        String emailFilter = email == null ? "" : email;
        Page<User> result = role == null || role.isBlank()
                ? userRepository.findByEmailContainingIgnoreCase(emailFilter, pageable)
                : userRepository.findByEmailContainingIgnoreCaseAndRoleIgnoreCase(emailFilter, role, pageable);
        return result.map(this::toAdminUserResponse);
    }

    private ProfileResponse toProfileResponse(User user) {
        return new ProfileResponse(
                user.getUsername(),
                user.getDisplayName(),
                user.getBio(),
                maskEmail(user.getEmail()),
                user.getRole()
        );
    }

    private AdminUserResponse toAdminUserResponse(User user) {
        return new AdminUserResponse(
                user.getUsername(),
                user.getDisplayName(),
                maskEmail(user.getEmail()),
                user.getRole(),
                user.isLockedNow()
        );
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
