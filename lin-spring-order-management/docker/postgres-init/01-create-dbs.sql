-- Each microservice owns its database (no shared tables across services).
-- orders_db is created via POSTGRES_DB; create the remaining three here.
CREATE DATABASE inventory_db;
CREATE DATABASE payments_db;
CREATE DATABASE notifications_db;
