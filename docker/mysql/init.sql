-- Runs once when the MySQL volume is first created.
-- All services currently share the retail_db schema (see config-server/config/<service>.yml);
-- the per-service databases are kept for anyone who sets MYSQL_DATABASE per container.
CREATE DATABASE IF NOT EXISTS retail_db;
CREATE DATABASE IF NOT EXISTS auth_db;
CREATE DATABASE IF NOT EXISTS customer_db;
CREATE DATABASE IF NOT EXISTS product_db;
CREATE DATABASE IF NOT EXISTS quote_db;
CREATE DATABASE IF NOT EXISTS proposal_db;
CREATE DATABASE IF NOT EXISTS payment_db;
CREATE DATABASE IF NOT EXISTS policy_db;
CREATE DATABASE IF NOT EXISTS notification_db;
GRANT ALL PRIVILEGES ON *.* TO 'insurance'@'%';
FLUSH PRIVILEGES;
