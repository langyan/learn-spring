package com.lin.spring.liquibase.controller;

import com.lin.spring.liquibase.dto.UserRequest;
import com.lin.spring.liquibase.dto.UserResponse;
import com.lin.spring.liquibase.model.User;
import com.lin.spring.liquibase.repository.UserRepository;
import com.lin.spring.liquibase.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        UserRequest request = new UserRequest(
                "testuser",
                "test@example.com",
                "password123",
                "1234567890",
                "Test Address"
        );
        testUser = new User();
        testUser.setUsername(request.getUsername());
        testUser.setEmail(request.getEmail());
        testUser.setPassword(request.getPassword());
        testUser.setPhone(request.getPhone());
        testUser.setAddress(request.getAddress());
        testUser.setEnabled(true);
        testUser = userRepository.save(testUser);
    }

    @Test
    void getAllUsers_shouldReturnListOfUsers() throws Exception {
        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$[0].username").exists())
                .andExpect(jsonPath("$[0].email").exists());
    }

    @Test
    void getUserById_shouldReturnUser() throws Exception {
        mockMvc.perform(get("/api/users/" + testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testUser.getId()))
                .andExpect(jsonPath("$.username").value(testUser.getUsername()))
                .andExpect(jsonPath("$.email").value(testUser.getEmail()));
    }

    @Test
    void getUserByUsername_shouldReturnUser() throws Exception {
        mockMvc.perform(get("/api/users/username/" + testUser.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(testUser.getUsername()))
                .andExpect(jsonPath("$.email").value(testUser.getEmail()));
    }

    @Test
    void createUser_shouldReturnCreatedUser() throws Exception {
        String userJson = """
                {
                    "username": "newuser",
                    "email": "newuser@example.com",
                    "password": "password123",
                    "phone": "9876543210",
                    "address": "New Address"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(userJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("newuser"))
                .andExpect(jsonPath("$.email").value("newuser@example.com"))
                .andExpect(jsonPath("$.phone").value("9876543210"))
                .andExpect(jsonPath("$.address").value("New Address"));
    }

    @Test
    void createUser_withInvalidData_shouldReturnBadRequest() throws Exception {
        String invalidUserJson = """
                {
                    "username": "ab",
                    "email": "invalid-email",
                    "password": "123"
                }
                """;

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidUserJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateUser_shouldReturnUpdatedUser() throws Exception {
        String updateJson = """
                {
                    "username": "updateduser",
                    "email": "updated@example.com",
                    "password": "newpassword123",
                    "phone": "9999999999",
                    "address": "Updated Address"
                }
                """;

        mockMvc.perform(put("/api/users/" + testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("updateduser"))
                .andExpect(jsonPath("$.email").value("updated@example.com"))
                .andExpect(jsonPath("$.address").value("Updated Address"));
    }

    @Test
    void deleteUser_shouldReturnNoContent() throws Exception {
        mockMvc.perform(delete("/api/users/" + testUser.getId()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + testUser.getId()))
                .andExpect(status().isNotFound());
    }

    @Test
    void disableUser_shouldDisableUser() throws Exception {
        mockMvc.perform(patch("/api/users/" + testUser.getId() + "/disable"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void enableUser_shouldEnableUser() throws Exception {
        testUser.setEnabled(false);
        userRepository.save(testUser);

        mockMvc.perform(patch("/api/users/" + testUser.getId() + "/enable"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    void recordLogin_shouldUpdateLastLogin() throws Exception {
        mockMvc.perform(post("/api/users/" + testUser.getId() + "/login"))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/users/" + testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastLogin").exists());
    }
}
