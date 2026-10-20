-- ============================================================
-- Seed data for RBAC demo
--   admin / admin123 -> ROLE_ADMIN (user:read, user:write, admin:read)
--   user  / user123  -> ROLE_USER  (user:read)
-- Passwords are pre-generated BCrypt hashes (strength 10).
-- ============================================================

-- Permissions
INSERT INTO permissions (id, name) VALUES (1, 'user:read');
INSERT INTO permissions (id, name) VALUES (2, 'user:write');
INSERT INTO permissions (id, name) VALUES (3, 'admin:read');

-- Roles
INSERT INTO roles (id, name) VALUES (1, 'ROLE_ADMIN');
INSERT INTO roles (id, name) VALUES (2, 'ROLE_USER');

-- Role <-> Permission
INSERT INTO role_permissions (role_id, permission_id) VALUES (1, 1);
INSERT INTO role_permissions (role_id, permission_id) VALUES (1, 2);
INSERT INTO role_permissions (role_id, permission_id) VALUES (1, 3);
INSERT INTO role_permissions (role_id, permission_id) VALUES (2, 1);

-- Users (password: admin123 / user123)
INSERT INTO users (id, enabled, password, username)
VALUES (1, TRUE, '$2a$10$NZojDpOBof1fkZujSPN9r.jsxpS3PX8jMHy3OfsYnV4JuPnMlMXqq', 'admin');
INSERT INTO users (id, enabled, password, username)
VALUES (2, TRUE, '$2a$10$ywe3E6C44GLehV8cR7IQpOWpAzjiCAzhXqq8zDLRL6Tjay6in2FZi', 'user');

-- User <-> Role
INSERT INTO user_roles (user_id, role_id) VALUES (1, 1);
INSERT INTO user_roles (user_id, role_id) VALUES (2, 2);
