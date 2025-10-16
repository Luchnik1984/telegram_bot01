package pro.sky.telegrambot.service;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.model.NotificationTask;
import pro.sky.telegrambot.repository.NotificationTaskRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationSchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationSchedulerService.class);

    private final NotificationTaskRepository repository;
    private final TelegramBot telegramBot;

    // Кэш для управления повторными отправками напоминаний:

    // Мапа для хранения времени ПЕРВОЙ отправки каждого напоминания
    // Key: ID напоминания, Value: время когда напоминание было отправлено в ПЕРВЫЙ раз
    private final Map<Long, LocalDateTime> notificationFirstSentTime = new ConcurrentHashMap<>();

    // Мапа для хранения количества отправок для каждого напоминания
    // Key: ID напоминания, Value: сколько раз уже было отправлено это напоминание
    private final Map<Long, Integer> notificationSendCount = new ConcurrentHashMap<>();

    // Мапа для хранения последних отправленных напоминаний по chatId (для команды "Ок")
    // Key: chatId пользователя, Value: ID последнего отправленного напоминания
    private final Map<Long, Long> lastSentNotifications = new ConcurrentHashMap<>();

    public NotificationSchedulerService(NotificationTaskRepository repository, TelegramBot telegramBot) {
        this.repository = repository;
        this.telegramBot = telegramBot;
        logger.info("NotificationSchedulerService initialized");
    }

    /**
     * ОСНОВНОЙ ШЕДУЛЕР - запускается КАЖДУЮ МИНУТУ
     * Логика работы:
     * 1. Ищет все активные напоминания (созданные менее 1 часа назад)
     * 2. Для каждого активного напоминания проверяет, нужно ли отправлять его сейчас
     * 3. Отправляет напоминания по расписанию: первая отправка точно в указанное время,
     *    затем повторные каждые 10 минут в течение 1 часа
     * 4. Выполняет автоочистку напоминаний старше 1 часа
     * Расписание: каждую минуту в 0 секунд (например: 12:00:00, 12:01:00, 12:02:00...)
     */
    @Scheduled(cron = "0 * * * * *")
    public void sendScheduledNotification() {
        logger.info("Scheduler started at {}", LocalDateTime.now());

        LocalDateTime currentTime = LocalDateTime.now();

        // Ищем ВСЕ активные напоминания (созданные менее 1 часа назад)
        LocalDateTime oneHourAgo = currentTime.minusHours(1);
        List<NotificationTask> activeNotifications = repository.findByCreatedAtAfter(oneHourAgo);

        logger.info("Found {} active notifications (created after {})",
                activeNotifications.size(), oneHourAgo);

        // Проверяем каждое активное напоминание на необходимость отправки
        int sentCount = 0;
        for (NotificationTask task : activeNotifications) {
            if (shouldSendNow(task, currentTime)) {
                sendNotification(task);
                sentCount++;
            }
        }

        // Автоочистка напоминаний старше 1 часа
        cleanupOldNotifications();

        logger.info("Scheduler finished at {}. Sent {} notifications",
                LocalDateTime.now(), sentCount);
    }

    /**
     * ОПРЕДЕЛЯЕТ НУЖНО ЛИ ОТПРАВЛЯТЬ НАПОМИНАНИЕ В ТЕКУЩИЙ МОМЕНТ
     * Алгоритм отправки:
     * - Первая отправка: точно в указанное пользователем время
     * - Повторные отправки: каждые 10 минут после первой отправки
     * - Всего отправок: 6 раз (0, 10, 20, 30, 40, 50 минут)
     * - Общая длительность: 1 час с момента первой отправки
     *
     * @param task напоминание для проверки
     * @param currentTime текущее время выполнения шедулера
     * @return true если напоминание нужно отправить сейчас
     */
    private boolean shouldSendNow(NotificationTask task, LocalDateTime currentTime) {
        Long taskId = task.getId();
        LocalDateTime notificationTime = task.getNotificationDateTime();
        LocalDateTime creationTime = task.getCreatedAt();

        // ПРОВЕРКА 1: Если время напоминания еще НЕ наступило - не отправляем
        // Это защита от напоминаний которые запланированы на будущее
        if (notificationTime.isAfter(currentTime)) {
            logger.debug("Notification ID: {} not ready yet (scheduled for {})", taskId, notificationTime);
            return false;
        }

        // ПРОВЕРКА 2: Если напоминание создано более 1 часа назад - прекращаем отправки
        // Это гарантирует автоудаление через 1 час независимо от количества отправок
        if (creationTime.plusHours(1).isBefore(currentTime)) {
            logger.debug("Notification ID: {} is older than 1 hour, skipping", taskId);
            return false;
        }

        // Получаем информацию о предыдущих отправках из кэша
        LocalDateTime firstSentTime = notificationFirstSentTime.get(taskId);
        Integer sendCount = notificationSendCount.getOrDefault(taskId, 0);

        // СЛУЧАЙ 1: ПЕРВАЯ ОТПРАВКА
        // Если напоминание еще ни разу не отправлялось - отправляем впервые
        if (firstSentTime == null) {
            // Проверяем, что это точное время напоминания (до минут)
            LocalDateTime notificationMinute = notificationTime.truncatedTo(ChronoUnit.MINUTES);
            LocalDateTime currentMinute = currentTime.truncatedTo(ChronoUnit.MINUTES);

            // Отправляем только если текущая минута совпадает с минутой напоминания
            if (notificationMinute.equals(currentMinute)) {
                // Сохраняем время первой отправки и устанавливаем счетчик в 1
                notificationFirstSentTime.put(taskId, currentTime);
                notificationSendCount.put(taskId, 1);
                logger.info("First send for notification ID: {} (scheduled for {})", taskId, notificationTime);
                return true;
            }
            return false;
        }

        // ПРОВЕРКА 3: Если с первой отправки прошло БОЛЕЕ 1 часа - прекращаем отправки
        // Напоминание отработало свой полный цикл (1 час с первой отправки)
        if (firstSentTime.plusHours(1).isBefore(currentTime)) {
            logger.info("Notification ID: {} completed 1-hour sending cycle", taskId);
            return false;
        }

        // СЛУЧАЙ 2: ПОВТОРНАЯ ОТПРАВКА
        // Проверяем что не превышен лимит в 6 отправок и пришло время для следующей отправки
        if (sendCount < 6 && shouldSendBasedOnInterval(firstSentTime, sendCount, currentTime)) {
            // Увеличиваем счетчик отправок
            notificationSendCount.put(taskId, sendCount + 1);
            logger.info("Resend #{}/6 for notification ID: {} (first sent at {})",
                    sendCount + 1, taskId, firstSentTime);
            return true;
        }

        // СЛУЧАЙ 3: Не подходит ни под один критерий отправки
        return false;
    }

    /**
     * ОПРЕДЕЛЯЕТ ВРЕМЯ СЛЕДУЮЩЕЙ ОТПРАВКИ НА ОСНОВЕ ИНТЕРВАЛОВ
     * Интервалы отправки (относительно времени первой отправки):
     * - Отправка 1: 0 минут (первая отправка)
     * - Отправка 2-6: +10,+20,+30,+40,+50
     * @param firstSentTime время первой отправки
     * @param sendCount количество уже выполненных отправок
     * @param currentTime текущее время
     * @return true если пришло время для очередной отправки
     */
    private boolean shouldSendBasedOnInterval(LocalDateTime firstSentTime, int sendCount, LocalDateTime currentTime) {
        // Рассчитываем время следующей отправки: первая_отправка + (счетчик * 10 минут)

        LocalDateTime nextSendTime = firstSentTime.plusMinutes(sendCount * 10L);

        // Округляем до минут для точного сравнения
        LocalDateTime nextSendMinute = nextSendTime.truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime currentMinute = currentTime.truncatedTo(ChronoUnit.MINUTES);

        // Отправляем если текущая минута совпадает с минутой следующей отправки
        boolean shouldSend = nextSendMinute.equals(currentMinute);

        logger.debug("Interval check: firstSent={}, count={}, nextSend={}, current={}, shouldSend={}",
                firstSentTime, sendCount, nextSendTime, currentTime, shouldSend);

        return shouldSend;
    }

    /**
     * ОТПРАВЛЯЕТ НАПОМИНАНИЕ ПОЛЬЗОВАТЕЛЮ В TELEGRAM
     * Формат сообщения включает:
     * - Текст напоминания
     * - Время напоминания
     * - Информацию о номере отправки (для повторных отправок)
     * - Инструкцию по отключению командой "Ок"
     * - Информацию об автоудалении через 1 час
     * Также обновляет кэш последних отправленных напоминаний для команды "Ок"
     *
     * @param task напоминание для отправки
     */
    private void sendNotification(NotificationTask task) {
        try {
            // Форматируем время для красивого отображения
            String formattedTime = task.getNotificationDateTime()
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));

            // Добавляем информацию о повторных отправках в сообщение
            Integer sendCount = notificationSendCount.getOrDefault(task.getId(), 1);
            String repeatInfo = "";
            if (sendCount > 1) {
                repeatInfo = "\nПовторная отправка #" + sendCount + "/6";
            }

            // Создаем форматированное сообщение
            String message = "Напоминание!" + repeatInfo + "\n\n" +
                    task.getNotificationText() + "\n\n" +
                    "Время: " + formattedTime + "\n\n" +
                    "Чтобы остановить напоминание, отправьте 'Ок'\n\n" +
                    "Напоминание автоматически удалится через 1 час после создания";

            // Создаем и отправляем сообщение через Telegram Bot API
            SendMessage sendMessage = new SendMessage(task.getChatId(), message);
            telegramBot.execute(sendMessage);

            // ОБНОВЛЯЕМ КЭШ ДЛЯ КОМАНДЫ "ОК"
            // Сохраняем ID этого напоминания как последнее отправленное для данного пользователя
            // Это нужно чтобы когда пользователь напишет "Ок" - мы знали какое напоминание удалять
            // Важно: обновляем при КАЖДОЙ отправке, чтобы команда "Ок" всегда работала на последнее сообщение
            lastSentNotifications.put(task.getChatId(), task.getId());

            logger.info("Notification sent to chat: {}, ID: {}, Send count: {}",
                    task.getChatId(), task.getId(), sendCount);

        } catch (Exception e) {
            logger.error("Failed to send notification to chat: {}", task.getChatId(), e);
        }
    }

    /**
     * АВТООЧИСТКА НАПОМИНАНИЙ СТАРШЕ 1 ЧАСА
     * Удаляет напоминания которые:
     * - Были созданы более 1 часа назад
     * - Независимо от того, сколько раз они были отправлены
     * - Гарантирует что старые напоминания не остаются в базе данных.
     * Вызывается каждую минуту из основного шедулера
     */
    private void cleanupOldNotifications() {
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        // Находим напоминания созданные более 1 часа назад
        List<NotificationTask> oldNotifications = repository.findByCreatedAtBefore(oneHourAgo);

        if (!oldNotifications.isEmpty()) {
            logger.info("Cleaning up {} old notifications", oldNotifications.size());

            for (NotificationTask task : oldNotifications) {
                logger.info("Deleting old notification ID: {}, Created: {}, Text: {}",
                        task.getId(), task.getCreatedAt(), task.getNotificationText());

                // Удаляем из базы данных
                repository.delete(task);

                // Очищаем кэши отправок
                notificationFirstSentTime.remove(task.getId());
                notificationSendCount.remove(task.getId());

                // Если это было последнее отправленное напоминание для чата - очищаем из кэша команды "Ок"
                Long lastSentId = lastSentNotifications.get(task.getChatId());
                if (lastSentId != null && lastSentId.equals(task.getId())) {
                    lastSentNotifications.remove(task.getChatId());
                    logger.info("Removed from lastSent cache: chatId={}, notificationId={}",
                            task.getChatId(), task.getId());
                }
            }
        }
    }

    /**
     * ДОПОЛНИТЕЛЬНАЯ ОЧИСТКА КЭША ОТ УСТАРЕВШИХ ЗАПИСЕЙ.
     * Запускается каждый час для:
     * - Удаления устаревших данных из кэша отправок
     * - Освобождения памяти
     * - Предотвращения утечек памяти.
     * Удаляет записи, где первая отправка была более 2 часов назад
     * (дает дополнительный час "буфер" на случай задержек)
     */
    @Scheduled(cron = "0 0 * * * *") // каждый час в 0 минут
    public void hourlyCacheCleanup() {
        logger.info("Hourly cache cleanup started");

        // Удаляем записи, где первая отправка была более 2 часов назад
        LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);
        int initialSize = notificationFirstSentTime.size();

        notificationFirstSentTime.entrySet().removeIf(entry ->
                entry.getValue().isBefore(twoHoursAgo)
        );

        // Синхронизируем кэш счетчиков отправок - удаляем записи, которых нет в первом кэше
        notificationSendCount.entrySet().removeIf(entry ->
                !notificationFirstSentTime.containsKey(entry.getKey())
        );

        int removedCount = initialSize - notificationFirstSentTime.size();
        logger.info("Cache cleanup completed. Removed {} entries, current cache size: {}",
                removedCount, notificationFirstSentTime.size());
    }

    // МЕТОДЫ ДЛЯ РАБОТЫ С КОМАНДОЙ "ОК"

    /**
     * Получает ID последнего отправленного напоминания для чата.
     * Используется когда пользователь отправляет "Ок" - чтобы знать какое напоминание удалять
     * @param chatId идентификатор чата
     * @return ID напоминания или null, если не найдено
     */
    public Long getLastSentNotificationId(Long chatId) {
        Long notificationId = lastSentNotifications.get(chatId);
        logger.info("Retrieved last sent notification for chatId= {}: {}", chatId, notificationId);
        return notificationId;
    }

    /**
     * Очищает последнее отправленное напоминание для чата.
     * Вызывается после успешного удаления напоминания по команде "Ок"
     * @param chatId идентификатор чата
     */
    public void clearLastSentNotification(Long chatId) {
        Long removedId = lastSentNotifications.remove(chatId);
        if (removedId != null) {
            logger.info("Removed last sent notification for chat {}: ID {}", chatId, removedId);
        } else {
            logger.info("No last sent notification found for chat {} to remove", chatId);
        }
    }

    /**
     * Получает количество закэшированных напоминаний (для отладки и мониторинга)
     * @return количество активных напоминаний в кэше
     */
    public int getCacheSize() {
        return notificationFirstSentTime.size();
    }

    /**
     * Получает информацию о конкретном напоминании в кэше (для отладки)
     * @param taskId ID напоминания
     * @return строка с информацией или null если не найдено
     */
    public String getCacheInfo(Long taskId) {
        LocalDateTime firstSent = notificationFirstSentTime.get(taskId);
        Integer count = notificationSendCount.get(taskId);

        if (firstSent == null) {
            return null;
        }

        return String.format("ID: %d, firstSent: %s, sendCount: %d",
                taskId, firstSent, count != null ? count : 0);
    }

    /**
     * Очищает весь кэш напоминаний (для тестирования/сброса)
     * ВНИМАНИЕ: Использовать только для отладки!
     */
    public void clearAllCache() {
        int firstSentSize = notificationFirstSentTime.size();
        int sendCountSize = notificationSendCount.size();
        int lastSentSize = lastSentNotifications.size();

        notificationFirstSentTime.clear();
        notificationSendCount.clear();
        lastSentNotifications.clear();

        logger.info("Cleared entire cache. Removed: firstSent={}, sendCount={}, lastSent={}",
                firstSentSize, sendCountSize, lastSentSize);
    }
}