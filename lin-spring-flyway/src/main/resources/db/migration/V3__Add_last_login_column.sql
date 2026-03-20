-- Add last_login column to users table
ALTER TABLE users ADD COLUMN last_login TIMESTAMP;

-- Create index on last_login for tracking user activity
CREATE INDEX idx_users_last_login ON users(last_login);
