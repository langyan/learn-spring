package com.lin.spring.security;

import com.lin.spring.security.security.AuthorityResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end coverage of the slim-token flow:
 * login -> identity-only JWT -> authorities resolved from cache ->
 * path-level (hasRole) and method-level (hasAuthority) authorization,
 * plus cache eviction making permission changes effective immediately.
 *
 * <p>Runs with the "test" profile: InMemoryAuthorityResolver replaces the
 * Redis-backed one, so no Redis server is needed for the test suite.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper; // Jackson 3 (tools.jackson) auto-configured by Boot 4

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthorityResolver authorityResolver;

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "%s", "password": "%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("accessToken")
                .asString();
    }

    @Test
    void loginWithValidAdminCredentialsReturnsToken() throws Exception {
        String token = loginAndGetToken("admin", "admin123");
        assertThat(token).isNotBlank().hasSizeGreaterThan(40);
    }

    @Test
    void tokenStaysSlimWithoutPermissionClaims() throws Exception {
        String token = loginAndGetToken("admin", "admin123");
        String payload = decodeJwtPayload(token);

        // identity only — authorities live in the cache, not in the token
        assertThat(payload).contains("\"sub\":\"admin\"");
        assertThat(payload).doesNotContain("roles");
        assertThat(payload).doesNotContain("perms");
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "admin", "password": "wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void loginWithBlankUsernameReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username": "", "password": "whatever1"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void protectedEndpointWithTamperedTokenReturns401() throws Exception {
        String token = loginAndGetToken("admin", "admin123");
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token + "x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminTokenCanAccessAdminEndpoint() throws Exception {
        String token = loginAndGetToken("admin", "admin123");
        mockMvc.perform(get("/api/v1/admin/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void userTokenIsForbiddenOnAdminEndpointByUrlRule() throws Exception {
        String token = loginAndGetToken("user", "user123");
        mockMvc.perform(get("/api/v1/admin/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void userTokenCanReadUsersByMethodPermission() throws Exception {
        String token = loginAndGetToken("user", "user123");
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'admin')]").exists())
                .andExpect(jsonPath("$[?(@.username == 'user')]").exists());
    }

    @Test
    void userTokenCannotDeleteUsersWithoutWritePermission() throws Exception {
        String token = loginAndGetToken("user", "user123");
        mockMvc.perform(delete("/api/v1/users/2")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void meEndpointReturnsCurrentUserWithRolesAndPermissions() throws Exception {
        String token = loginAndGetToken("user", "user123");
        mockMvc.perform(get("/api/v1/users/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.permissions[0]").value("user:read"));
    }

    /**
     * Core value of the slim-token + cache pattern: revoking permissions in
     * the database takes effect on already-issued tokens once the cache
     * entry is evicted (no need to wait for token expiry or re-login).
     */
    @Test
    void permissionRevocationTakesEffectAfterCacheEviction() throws Exception {
        String token = loginAndGetToken("user", "user123");

        try {
            // 1. baseline: 'user' has user:read -> 200
            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            // 2. revoke all roles of 'user' directly in the database
            jdbcTemplate.update("""
                    DELETE FROM user_roles
                    WHERE user_id = (SELECT id FROM users WHERE username = 'user')
                    """);

            // 3. cache still holds the old authorities -> still 200 (stale window)
            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk());

            // 4. evict -> next request reloads from DB -> no authority -> 403
            authorityResolver.evict("user");
            mockMvc.perform(get("/api/v1/users")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden());
        } finally {
            // restore seed data so other tests are not affected
            jdbcTemplate.update("""
                    INSERT INTO user_roles (user_id, role_id)
                    SELECT u.id, r.id FROM users u, roles r
                    WHERE u.username = 'user' AND r.name = 'ROLE_USER'
                      AND NOT EXISTS (SELECT 1 FROM user_roles ur WHERE ur.user_id = u.id AND ur.role_id = r.id)
                    """);
            authorityResolver.evict("user");
        }
    }

    @Test
    void adminCanEvictAuthorityCacheViaEndpoint() throws Exception {
        String token = loginAndGetToken("admin", "admin123");
        mockMvc.perform(delete("/api/v1/admin/authority-cache/user")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("authority cache evicted for 'user'"));
    }

    private static String decodeJwtPayload(String token) {
        String base64 = token.split("\\.")[1];
        // JWT segments omit '=' padding; restore it before decoding
        String padded = base64 + "=".repeat((4 - base64.length() % 4) % 4);
        return new String(Base64.getUrlDecoder().decode(padded), StandardCharsets.UTF_8);
    }
}
