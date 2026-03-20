-- Insert sample users
INSERT INTO users (username, email, password, phone, address, enabled) VALUES
('john_doe', 'john.doe@example.com', 'password123', '+1234567890', '123 Main St, New York, NY 10001', TRUE),
('jane_smith', 'jane.smith@example.com', 'password456', '+1234567891', '456 Oak Ave, Los Angeles, CA 90001', TRUE),
('bob_wilson', 'bob.wilson@example.com', 'password789', '+1234567892', '789 Pine Rd, Chicago, IL 60601', TRUE),
('alice_brown', 'alice.brown@example.com', 'passwordabc', '+1234567893', '321 Elm St, Houston, TX 77001', FALSE),
('charlie_davis', 'charlie.davis@example.com', 'passworddef', '+1234567894', '654 Maple Dr, Phoenix, AZ 85001', TRUE);
