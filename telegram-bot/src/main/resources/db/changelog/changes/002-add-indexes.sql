--liquibase formatted sql

--changeset telegram-bot:6
CREATE INDEX idx_chat_chat_id ON chat(chat_id);
CREATE INDEX idx_chat_last_activity ON chat(last_activity);

--changeset telegram-bot:7
CREATE INDEX idx_template_chat_id ON notification_template(chat_id);
CREATE INDEX idx_template_is_active ON notification_template(is_active);
CREATE INDEX idx_template_usage_count ON notification_template(usage_count);

--changeset telegram-bot:8
CREATE INDEX idx_instance_chat_id ON notification_instance(chat_id);
CREATE INDEX idx_instance_status ON notification_instance(status);
CREATE INDEX idx_instance_scheduled_time ON notification_instance(scheduled_time);
CREATE INDEX idx_instance_created_at ON notification_instance(created_at);
CREATE INDEX idx_instance_template_id ON notification_instance(template_id);

--changeset telegram-bot:9
CREATE INDEX idx_delivery_instance_id ON notification_delivery(instance_id);
CREATE INDEX idx_delivery_delivery_time ON notification_delivery(delivery_time);
CREATE INDEX idx_delivery_success ON notification_delivery(success);

--changeset telegram-bot:10
CREATE INDEX idx_sending_state_instance_id ON notification_sending_state(instance_id);
CREATE INDEX idx_sending_state_sending_phase ON notification_sending_state(sending_phase);
CREATE INDEX idx_sending_state_next_scheduled ON notification_sending_state(next_scheduled_send);
CREATE INDEX idx_sending_state_pause_until ON notification_sending_state(pause_until);