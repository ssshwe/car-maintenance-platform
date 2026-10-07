CREATE TABLE IF NOT EXISTS sys_user (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, login_name VARCHAR(50) NOT NULL UNIQUE,
 phone VARCHAR(20) NOT NULL UNIQUE, password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL,
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL
);
CREATE TABLE IF NOT EXISTS store (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL UNIQUE,
 name VARCHAR(100) NOT NULL, contact_name VARCHAR(50) NOT NULL, contact_phone VARCHAR(20) NOT NULL,
 address VARCHAR(255) NOT NULL, business_hours VARCHAR(100) NOT NULL,
 audit_status VARCHAR(20) NOT NULL, audit_user_id BIGINT, audit_at DATETIME, audit_comment VARCHAR(500),
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
CREATE TABLE IF NOT EXISTS product (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, sku VARCHAR(50) NOT NULL UNIQUE, name VARCHAR(120) NOT NULL UNIQUE,
 category VARCHAR(80) NOT NULL, description VARCHAR(2000), image_url VARCHAR(500),
 price DECIMAL(10,2) NOT NULL CHECK(price >= 0), labor_fee DECIMAL(10,2) NOT NULL CHECK(labor_fee >= 0),
 status VARCHAR(20) NOT NULL, created_by BIGINT NOT NULL,
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 FOREIGN KEY (created_by) REFERENCES sys_user(id)
);
CREATE TABLE IF NOT EXISTS inventory (
 product_id BIGINT PRIMARY KEY, on_hand_quantity INT NOT NULL CHECK(on_hand_quantity >= 0),
 reserved_quantity INT NOT NULL CHECK(reserved_quantity >= 0),
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 FOREIGN KEY (product_id) REFERENCES product(id), CHECK(reserved_quantity <= on_hand_quantity)
);
CREATE TABLE IF NOT EXISTS cart_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 quantity INT NOT NULL CHECK(quantity > 0 AND quantity <= 99), created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 UNIQUE(user_id, product_id), FOREIGN KEY (user_id) REFERENCES sys_user(id), FOREIGN KEY (product_id) REFERENCES product(id)
);
CREATE TABLE IF NOT EXISTS service_order (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR(32) NOT NULL UNIQUE, user_id BIGINT NOT NULL, store_id BIGINT NOT NULL,
 status VARCHAR(30) NOT NULL, product_amount DECIMAL(10,2) NOT NULL, labor_amount DECIMAL(10,2) NOT NULL,
 total_amount DECIMAL(10,2) NOT NULL, review_user_id BIGINT, review_at DATETIME, review_comment VARCHAR(500),
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 FOREIGN KEY (user_id) REFERENCES sys_user(id), FOREIGN KEY (store_id) REFERENCES store(id)
);
CREATE TABLE IF NOT EXISTS service_order_item (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 sku_snapshot VARCHAR(50) NOT NULL, name_snapshot VARCHAR(120) NOT NULL, unit_price_snapshot DECIMAL(10,2) NOT NULL,
 unit_labor_fee_snapshot DECIMAL(10,2) NOT NULL, quantity INT NOT NULL CHECK(quantity > 0), line_amount DECIMAL(10,2) NOT NULL,
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 FOREIGN KEY (order_id) REFERENCES service_order(id), FOREIGN KEY (product_id) REFERENCES product(id)
);
CREATE TABLE IF NOT EXISTS store_service_slot (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, store_id BIGINT NOT NULL, service_date DATE NOT NULL,
 start_time VARCHAR(5) NOT NULL, end_time VARCHAR(5) NOT NULL, capacity INT NOT NULL CHECK(capacity > 0),
 reserved_count INT NOT NULL CHECK(reserved_count >= 0), status VARCHAR(20) NOT NULL,
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 UNIQUE(store_id, service_date, start_time), FOREIGN KEY (store_id) REFERENCES store(id), CHECK(reserved_count <= capacity)
);
CREATE TABLE IF NOT EXISTS appointment (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL UNIQUE, user_id BIGINT NOT NULL,
 store_id BIGINT NOT NULL, slot_id BIGINT NOT NULL, status VARCHAR(20) NOT NULL, arrived_at DATETIME,
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 FOREIGN KEY (order_id) REFERENCES service_order(id), FOREIGN KEY (user_id) REFERENCES sys_user(id),
 FOREIGN KEY (store_id) REFERENCES store(id), FOREIGN KEY (slot_id) REFERENCES store_service_slot(id)
);
CREATE TABLE IF NOT EXISTS service_voucher (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL UNIQUE, store_id BIGINT NOT NULL,
 code VARCHAR(10) NOT NULL UNIQUE, status VARCHAR(20) NOT NULL, used_at DATETIME,
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 FOREIGN KEY (order_id) REFERENCES service_order(id), FOREIGN KEY (store_id) REFERENCES store(id)
);
CREATE TABLE IF NOT EXISTS maintenance_record (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL UNIQUE, appointment_id BIGINT NOT NULL UNIQUE,
 user_id BIGINT NOT NULL, store_id BIGINT NOT NULL, operator_id BIGINT NOT NULL,
 status VARCHAR(20) NOT NULL, service_items VARCHAR(2000), note VARCHAR(2000), started_at DATETIME NOT NULL, completed_at DATETIME,
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 FOREIGN KEY (order_id) REFERENCES service_order(id), FOREIGN KEY (appointment_id) REFERENCES appointment(id),
 FOREIGN KEY (user_id) REFERENCES sys_user(id), FOREIGN KEY (store_id) REFERENCES store(id), FOREIGN KEY (operator_id) REFERENCES sys_user(id)
);
CREATE TABLE IF NOT EXISTS review (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, user_id BIGINT NOT NULL, target_type VARCHAR(20) NOT NULL,
 target_id BIGINT NOT NULL, rating INT NOT NULL CHECK(rating >= 1 AND rating <= 5), content VARCHAR(1000) NOT NULL,
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL,
 UNIQUE(order_id, target_type, target_id), FOREIGN KEY (order_id) REFERENCES service_order(id), FOREIGN KEY (user_id) REFERENCES sys_user(id)
);
CREATE TABLE IF NOT EXISTS audit_log (
 id BIGINT AUTO_INCREMENT PRIMARY KEY, actor_id BIGINT NOT NULL, action VARCHAR(50) NOT NULL,
 target_type VARCHAR(30) NOT NULL, target_id BIGINT NOT NULL, detail VARCHAR(2000), trace_id VARCHAR(36) NOT NULL,
 created_at DATETIME NOT NULL, updated_at DATETIME NOT NULL, FOREIGN KEY (actor_id) REFERENCES sys_user(id)
);
