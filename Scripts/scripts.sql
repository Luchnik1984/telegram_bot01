
-- Показать таблицу
SELECT * FROM notification_task;

-- Полностью очистить таблицу
DELETE FROM notification_task;

-- Или сбросить автоинкремент (если нужно)
TRUNCATE TABLE notification_task RESTART IDENTITY;