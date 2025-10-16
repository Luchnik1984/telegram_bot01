
-- Показать таблицу
SELECT * FROM notification_task;

-- Полностью очистить таблицу
TRUNCATE TABLE notification_task RESTART IDENTITY;

-- Или сбросить автоинкремент (если нужно)
DELETE FROM notification_task
WHERE id = 15;

-- Удалить конкретное напоминание по ID
DELETE FROM notification_task;
