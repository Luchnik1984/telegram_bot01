package pro.sky.telegrambot.service;

import pro.sky.telegrambot.model.NotificationTask;
import pro.sky.telegrambot.model.ParseResult;
import pro.sky.telegrambot.repository.NotificationTaskRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@Transactional
public class NotificationTaskService {

    private final NotificationTaskRepository repository;
    private static final Logger logger = LoggerFactory.getLogger(NotificationTaskService.class);

    // РЕГУЛЯРНЫЕ ВЫРАЖЕНИЯ И ФОРМАТТЕРЫ ДЛЯ ПАРСИНГА
    private static final Pattern PATTERN = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4}\\s\\d{2}:\\d{2})\\s*(.*)");
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public NotificationTaskService(NotificationTaskRepository repository) {
        this.repository = repository;
        logger.info("NotificationTaskService initialized with extended functionality");
    }

    // СУЩЕСТВУЮЩИЕ МЕТОДЫ (старая архитектура) - ДЛЯ ОБРАТНОЙ СОВМЕСТИМОСТИ

    /**
     * СОЗДАТЬ НАПОМИНАНИЕ (совместимость со старым кодом)
     * Используется старыми компонентами для создания напоминаний
     * в старом формате (без расширенного состояния)
     * @param chatId идентификатор чата пользователя
     * @param text текст напоминания
     * @param dateTime время напоминания
     * @return созданное напоминание
     */
    public NotificationTask createNotification(Long chatId, String text, LocalDateTime dateTime) {
        logger.info("Creating notification via legacy method - chat: {}, time: {}, text: {}",
                chatId, dateTime, text);

        NotificationTask task = new NotificationTask(chatId, text, dateTime);
        NotificationTask savedTask = repository.save(task);

        logger.info("Successfully created legacy notification with ID: {}", savedTask.getId());
        return savedTask;
    }

    /**
     * ПАРСИНГ И СОХРАНЕНИЕ НАПОМИНАНИЯ (старый метод)
     * Используется старым кодом для парсинга сообщения и сразу сохранения в БД
     * @param messageText текст сообщения от пользователя
     * @param chatId идентификатор чата
     * @return результат парсинга с напоминанием или ошибкой
     */
    public ParseResult parseAndSaveNotification(String messageText, long chatId) {
        logger.info("Parsing and saving notification via legacy method - chat: {}, message: {}",
                chatId, messageText);

        // Используем общий метод парсинга
        ParseResult parseResult = parseMessage(messageText);

        if (parseResult.isSuccess()) {
            // Если парсинг успешен - сохраняем в БД старым способом
            NotificationTask task = parseResult.getTask();
            NotificationTask savedTask = createNotification(chatId, task.getNotificationText(), task.getNotificationDateTime());

            // Возвращаем результат с сохраненной задачей
            return ParseResult.success(savedTask);
        } else {
            // Возвращаем ошибку парсинга
            return parseResult;
        }
    }

    // НОВЫЕ МЕТОДЫ ДЛЯ СОВМЕСТИМОСТИ С НОВОЙ АРХИТЕКТУРОЙ

    /**
     * ПАРСИНГ СООБЩЕНИЯ (новый метод)
     * Только парсит сообщение без сохранения в БД.
     * Используется новым OrchestratorService для создания напоминаний
     * в новой архитектуре
     * @param messageText текст сообщения от пользователя
     * @return результат парсинга с временным напоминанием или ошибкой
     */
    public ParseResult parseMessage(String messageText) {
        logger.info("Parsing message: '{}'", messageText);

        // ПРОВЕРКА 1: ПУСТОЕ СООБЩЕНИЕ
        if (messageText == null || messageText.trim().isEmpty()) {
            logger.warn("Empty message received");
            return ParseResult.error("Сообщение не может быть пустым!");
        }

        // ПРОВЕРКА 2: СООТВЕТСТВИЕ ПАТТЕРНУ
        Matcher matcher = PATTERN.matcher(messageText);
        if (!matcher.matches()) {
            logger.warn("Message does not match pattern: '{}'", messageText);

            // Проверяем частный случай - только дата без текста
            if (messageText.matches("\\d{2}\\.\\d{2}\\.\\d{4}\\s\\d{2}:\\d{2}")) {
                return ParseResult.error("Текст напоминания не может быть пустым! Добавьте текст после даты.");
            }
            return ParseResult.error("Неверный формат сообщения! Используйте: дд.мм.гггг чч:мм Текст напоминания");
        }

        // ПАРСИНГ ДАННЫХ ИЗ СООБЩЕНИЯ
        try {
            String dateTimeString = matcher.group(1);
            String notificationText = matcher.group(2).trim();

            logger.info("Pattern matched - DateTime: '{}', Text: '{}'", dateTimeString, notificationText);

            // ПРОВЕРКА 3: ПУСТОЙ ТЕКСТ ПОСЛЕ ОБРЕЗКИ
            if (notificationText.isEmpty()) {
                logger.warn("Notification text is empty after trimming");
                return ParseResult.error("Текст напоминания не может быть пустым!");
            }

            // ПАРСИНГ ДАТЫ И ВРЕМЕНИ
            LocalDateTime notificationDateTime = LocalDateTime.parse(dateTimeString, FORMATTER);
            logger.info("Parsed LocalDateTime: {}", notificationDateTime);

            // ПРОВЕРКА 4: ДАТА В БУДУЩЕМ
            LocalDateTime now = LocalDateTime.now();
            logger.info("Current time: {}", now);
            if (notificationDateTime.isBefore(now)) {
                logger.warn("Date is in the past: {} < {}", notificationDateTime, now);
                return ParseResult.error("Нельзя создать напоминание в прошлом!");
            }

            // СОЗДАНИЕ ВРЕМЕННОЙ ЗАДАЧИ ДЛЯ ПЕРЕДАЧИ В ORCHESTRATOR
            // Не сохраняем в БД - это сделает OrchestratorService в новой архитектуре
            NotificationTask task = new NotificationTask();
            task.setNotificationText(notificationText);
            task.setNotificationDateTime(notificationDateTime);

            logger.info("Successfully parsed message into temporary task");
            return ParseResult.success(task);

        } catch (DateTimeParseException e) {
            logger.error("Failed to parse date/time: {}", e.getMessage());
            return ParseResult.error("Неверный формат даты или времени! Используйте: дд.мм.гггг чч:мм");
        } catch (Exception e) {
            logger.error("Unexpected error during message parsing", e);
            return ParseResult.error("Произошла непредвиденная ошибка при обработке сообщения");
        }
    }

    /**
     * ОБНОВИТЬ СЧЕТЧИК ОТПРАВОК.
     * Вызывается при каждой успешной отправке напоминания
     *
     * @param taskId ID напоминания
     */
    public void incrementSendCount(Long taskId) {
        logger.debug("Incrementing send count for task: {}", taskId);

        repository.incrementSendCount(taskId, LocalDateTime.now());

        // УСТАНОВИТЬ ВРЕМЯ ПЕРВОЙ ОТПРАВКИ ПРИ ПЕРВОЙ ОТПРАВКЕ
        repository.findById(taskId).ifPresent(task -> {
            if (task.getFirstSentTime() == null) {
                task.setFirstSentTime(LocalDateTime.now());
                task.setStatus("ACTIVE");
                repository.save(task);
                logger.info("Set first sent time for task: {}", taskId);
            }
        });

        logger.debug("Successfully incremented send count for task: {}", taskId);
    }

    /**
     * ОБНОВИТЬ СТАТУС НАПОМИНАНИЯ
     * @param taskId ID напоминания
     * @param status новый статус
     */
    public void updateStatus(Long taskId, String status) {
        logger.info("Updating status for task: {} to: {}", taskId, status);
        repository.updateStatus(taskId, status);
    }

    /**
     * НАЙТИ НАПОМИНАНИЯ ДЛЯ ТЕКУЩЕЙ МИНУТЫ.
     * Используется для обратной совместимости со старым шедулером
     * @param currentTime текущее время
     * @return список напоминаний для отправки
     */
    public List<NotificationTask> findDueNotifications(LocalDateTime currentTime) {
        LocalDateTime oneHourAgo = currentTime.minusHours(1);
        List<NotificationTask> dueTasks = repository.findNotificationsForCurrentMinute(currentTime, oneHourAgo);

        logger.debug("Found {} due notifications for time: {}", dueTasks.size(), currentTime);
        return dueTasks;
    }

    /**
     * НАЙТИ ПОСЛЕДНЕЕ АКТИВНОЕ НАПОМИНАНИЕ ДЛЯ ЧАТА.
     * Используется для обработки команды "Ок"
     * @param chatId идентификатор чата
     * @return Optional с последним активным напоминанием
     */
    public Optional<NotificationTask> findLastActiveByChatId(Long chatId) {
        Optional<NotificationTask> lastActive = repository.findLastActiveByChatId(chatId);

        if (lastActive.isPresent()) {
            logger.debug("Found last active notification for chat: {} - ID: {}",
                    chatId, lastActive.get().getId());
        } else {
            logger.debug("No active notifications found for chat: {}", chatId);
        }

        return lastActive;
    }

    /**
     * ПОМЕТИТЬ НАПОМИНАНИЕ КАК ВЫПОЛНЕННОЕ
     *
     * @param taskId ID напоминания
     */
    public void markAsCompleted(Long taskId) {
        logger.info("Marking task as completed: {}", taskId);
        updateStatus(taskId, "COMPLETED");
    }

    /**
     * ПОМЕТИТЬ НАПОМИНАНИЕ КАК ИСТЕКШЕЕ
     *
     * @param taskId ID напоминания
     */
    public void markAsExpired(Long taskId) {
        logger.info("Marking task as expired: {}", taskId);
        updateStatus(taskId, "EXPIRED");
    }

    /**
     * ОТМЕНИТЬ НАПОМИНАНИЕ
     *
     * @param taskId ID напоминания
     */
    public void markAsCancelled(Long taskId) {
        logger.info("Marking task as cancelled: {}", taskId);
        updateStatus(taskId, "CANCELLED");
    }

    /**
     * ВЫПОЛНИТЬ ОЧИСТКУ СТАРЫХ НАПОМИНАНИЙ.
     * Удаляет напоминания старше 7 дней со статусом COMPLETED, EXPIRED, CANCELLED
     */
    public void cleanupOldTasks() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7);
        int deletedCount = repository.deleteOldCompletedTasks(cutoffTime);

        logger.info("Cleaned up {} old completed tasks", deletedCount);
    }

    /**
     * ПОМЕТИТЬ ИСТЕКШИЕ НАПОМИНАНИЯ.
     * Находит напоминания старше 1 часа и помечает их как EXPIRED
     */
    public void markExpiredTasks() {
        LocalDateTime expiryTime = LocalDateTime.now().minusHours(1);
        int expiredCount = repository.markExpiredNotifications(expiryTime);

        if (expiredCount > 0) {
            logger.info("Marked {} tasks as expired", expiredCount);
        }
    }

    /**
     * ПОЛУЧИТЬ СТАТИСТИКУ ПО ЧАТУ
     *
     * @param chatId идентификатор чата
     * @return строка со статистикой
     */
    public String getChatStatistics(Long chatId) {
        List<NotificationTask> allTasks = repository.findByChatId(chatId);

        long total = allTasks.size();
        long completed = allTasks.stream().filter(t -> "COMPLETED".equals(t.getStatus())).count();
        long active = allTasks.stream().filter(t -> "ACTIVE".equals(t.getStatus())).count();
        long expired = allTasks.stream().filter(t -> "EXPIRED".equals(t.getStatus())).count();

        return String.format(
                " Статистика ваших напоминаний:\n" +
                        " Всего создано: %d\n" +
                        " Выполнено: %d\n" +
                        " Активных: %d\n" +
                        " Истекших: %d\n" +
                        " Процент выполнения: %.1f%%",
                total, completed, active, expired,
                total > 0 ? (double) completed / total * 100 : 0.0
        );
    }

    /**
     * ПРОВЕРИТЬ СУЩЕСТВОВАНИЕ НАПОМИНАНИЯ
     *
     * @param taskId ID напоминания
     * @return true если напоминание существует
     */
    public boolean exists(Long taskId) {
        return repository.existsById(taskId);
    }

    /**
     * ПОЛУЧИТЬ НАПОМИНАНИЕ ПО ID
     *
     * @param taskId ID напоминания
     * @return Optional с напоминанием
     */
    public Optional<NotificationTask> findById(Long taskId) {
        return repository.findById(taskId);
    }

    /**
     * УДАЛИТЬ НАПОМИНАНИЕ
     *
     * @param taskId ID напоминания
     */
    public void deleteTask(Long taskId) {
        logger.info("Deleting task: {}", taskId);
        repository.deleteById(taskId);
    }
}

