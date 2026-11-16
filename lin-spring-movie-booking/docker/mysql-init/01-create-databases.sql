-- Creates the second logical database next to movie_booking (created via MYSQL_DATABASE).
-- Catalog reads (movie-service) and hot-row writes (booking-service) are isolated at schema level.
CREATE DATABASE IF NOT EXISTS movie_catalog DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS movie_booking DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
