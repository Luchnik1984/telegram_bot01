--liquibase formatted sql

--changeset telegram-bot:1
CREATE TABLE chat (
                      id BIGSERIAL PRIMARY KEY,
                      chat_id BIGINT UNIQUE NOT NULL,
                      username VARCHAR(255),
                      first_name VARCHAR(255),
                      created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                      last_activity TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--changeset telegram-bot:2
CREATE TABLE notification_template (
                                       id BIGSERIAL PRIMARY KEY,
                                       chat_id BIGINT NOT NULL REFERENCES chat(id) ON DELETE CASCADE,
                                       template_text TEXT NOT NULL,
                                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                       is_active BOOLEAN DEFAULT true NOT NULL,
                                       usage_count INTEGER DEFAULT 0 NOT NULL
);

--changeset telegram-bot:3
CREATE TABLE notification_instance (
                                       id BIGSERIAL PRIMARY KEY,
                                       chat_id BIGINT NOT NULL REFERENCES chat(id) ON DELETE CASCADE,
                                       template_id BIGINT REFERENCES notification_template(id) ON DELETE SET NULL,
                                       notification_text TEXT NOT NULL,
                                       scheduled_time TIMESTAMP NOT NULL,
                                       created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
                                       status VARCHAR(20) DEFAULT 'PENDING' NOT NULL
);

--changeset telegram-bot:4
CREATE TABLE notification_delivery (
                                       id BIGSERIAL PRIMARY KEY,
                                       instance_id BIGINT NOT NULL REFERENCES notification_instance(id) ON DELETE CASCADE,
                                       delivery_time TIMESTAMP NOT NULL,
                                       attempt_number INTEGER NOT NULL,
                                       success BOOLEAN DEFAULT true NOT NULL,
                                       error_message TEXT,
                                       message_id BIGINT,
                                       delivery_duration_ms INTEGER
);

--changeset telegram-bot:5
CREATE TABLE notification_sending_state (
                                            id BIGSERIAL PRIMARY KEY,
                                            instance_id BIGINT NOT NULL REFERENCES notification_instance(id) ON DELETE CASCADE UNIQUE,
                                            first_sent_time TIMESTAMP,
                                            last_sent_time TIMESTAMP,
                                            send_count INTEGER DEFAULT 0 NOT NULL,
                                            max_attempts INTEGER DEFAULT 6 NOT NULL,
                                            next_scheduled_send TIMESTAMP,
                                            sending_phase VARCHAR(20) DEFAULT 'INITIAL' NOT NULL,
                                            pause_until TIMESTAMP
);