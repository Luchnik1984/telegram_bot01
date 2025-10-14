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
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class NotificationSchedulerService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationSchedulerService.class);

    private final NotificationTaskRepository repository;
    private final TelegramBot telegramBot;

    // Мапа для хранения последних отправленных напоминаний по chatId
    // Key: chatId, Value: notificationId
    private final Map<Long, Long> lastSentNotifications = new ConcurrentHashMap<>();

    public NotificationSchedulerService(NotificationTaskRepository repository, TelegramBot telegramBot) {
        this.repository = repository;
        this.telegramBot = telegramBot;
    }

    /**
     * Шедулер, который запускается каждые 10 минут,
     * ищет напоминания для отправки и отправляет их
     */
    @Scheduled(cron = "0 */10 * * * *") //каждые 10 минут в 0 секунд
    public void sendScheduledNotification() {
        logger.info("Scheduler started at {}", LocalDateTime.now());

        // Обрезаем время до минут
        LocalDateTime currentTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        logger.info("Searching notifications for time: {}", currentTime);

        // Ищем напоминания для текущей минуты
        List<NotificationTask> notificationToSend = repository.findByNotificationToSend(currentTime);
        logger.info("Found {} notifications to send", notificationToSend.size());

        // Отправляем каждое напоминание
        for (NotificationTask task : notificationToSend) {
            sendNotification(task);
        }
        logger.info("Scheduler stopped at {}", LocalDateTime.now());
    }

    /**
     * Отправляет напоминание пользователю с запросом подтверждения
     * @param task напоминание для отправки
     */
    private void sendNotification(NotificationTask task) {
        try {
            String message = "! Напоминание !\n"+
                     task.getNotificationText()+
                    "\n\n Остановить напоминание, отправьте 'Ок' для отключения "+
                    "\n\n ! Напоминание автоматически удалится через 1 час";

            SendMessage sendMessage = new SendMessage(task.getChatId(), message);

            telegramBot.execute(sendMessage);
            logger.info("Notification sent to chat: {}, text: {}, ID: {}",
                    task.getChatId(),
                    task.getNotificationText(),
                    task.getId());

            // Запоминаем последнее отправленное напоминание для этого пользователя
            lastSentNotifications.put(task.getChatId(), task.getId());
            logger.info("Cashed last sent notification: chatId={},notificationId={}", task.getChatId(), task.getId());

        } catch (Exception e) {
            logger.error("Failed to send notification to chat: {}", task.getChatId(), e);
        }
    }

    /**
     * Получает ID последнего отправленного напоминания для чата
     * @param chatId идентификатор чата
     * @return ID напоминания или null, если не найдено
     */
    public Long getLastSentNotificationId(Long chatId) {
        Long notificationId=lastSentNotifications.get(chatId);
        logger.info("Retrieved last sent notification for chatId= {}: {}", chatId, notificationId);
        return notificationId;
    }

    /**
     * Очищает последнее отправленное напоминание для чата
     * @param chatId идентификатор чата
     */
    public void clearLastSentNotification(Long chatId) {
        Long removedId = lastSentNotifications.remove(chatId);
        if (removedId != null) {
            logger.info("Removed last sent notification for chat {}: ID {}", chatId, removedId);
        } else {
            logger.info("No last sent notification found for chat {} to remove", chatId);
        }
        lastSentNotifications.remove(chatId);
    }

    /**
     * Получает количество закэшированных напоминаний (для отладки)
     * @return количество записей в кэше
     */
    public int getCacheSize(){
        return lastSentNotifications.size();
    }

    /**
     * Очищает весь кэш напоминаний (для тестирования/сброса)
     */
    public void clearAllCache(){
        int size = lastSentNotifications.size();
        lastSentNotifications.clear();
        logger.info("Cleared entire cache, removed {} entries", size);
    }

    /**
     * Очищает напоминания старше 1 часа
     * Запускается каждый час
     */
    @Scheduled(cron = "0 0 * * * *") // каждый час в 0 минут
    public void cleanupOldNotifications() {
        logger.info("Starting cleanup of old notifications");

        // Напоминания старше 1 часа
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        List<NotificationTask> oldNotifications = repository.findByNotificationDateTimeBefore(oneHourAgo);

        if (!oldNotifications.isEmpty()) {
            int deletedCount = oldNotifications.size();
            repository.deleteAll(oldNotifications);
            logger.info("Cleaned up {} old notifications (older than {})", deletedCount, oneHourAgo);

            // Также очищаем кэш для удалённых напоминаний
            for (NotificationTask task : oldNotifications) {
                Long cachedId = lastSentNotifications.get(task.getChatId());
                if (cachedId != null && cachedId.equals(task.getId())) {
                    lastSentNotifications.remove(task.getChatId());
                    logger.info("Removed from cache: chatId={}, notificationId={}", task.getChatId(), task.getId());
                }
            }
        } else {
            logger.info("No old notifications to clean up");
        }
    }
}
