--liquibase formatted sql

--changeset SendMeReminder:1
CREATE TABLE notification_task (
                                   id BIGSERIAL PRIMARY KEY,
                                   chat_id BIGINT NOT NULL,
                                   notification_text TEXT NOT NULL,
                                   notification_date_time TIMESTAMP NOT NULL,
                                   created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

--changeset SendMeReminder:2
CREATE INDEX idx_notification_task_datetime ON notification_task(notification_date_time);

--changeset SendMeReminder:3
CREATE INDEX idx_notification_task_chat_id ON notification_task(chat_id);