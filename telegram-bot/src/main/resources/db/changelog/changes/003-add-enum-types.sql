--liquibase formatted sql

--changeset telegram-bot:11
CREATE TYPE notification_status AS ENUM ('PENDING', 'ACTIVE', 'COMPLETED', 'EXPIRED', 'CANCELLED');
CREATE TYPE sending_phase AS ENUM ('INITIAL', 'REPEAT', 'COMPLETED', 'PAUSED');

--changeset telegram-bot:12
ALTER TABLE notification_instance
ALTER COLUMN status TYPE notification_status USING status::notification_status;

--changeset telegram-bot:13
ALTER TABLE notification_sending_state
ALTER COLUMN sending_phase TYPE sending_phase USING sending_phase::sending_phase;