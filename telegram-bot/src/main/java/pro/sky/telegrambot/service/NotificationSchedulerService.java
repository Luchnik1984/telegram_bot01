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

    // Мапа для хранения количества отправок для каждого напоминания
    // Key: ID напоминания, Value: количество отправок
    private final Map<Long, Integer> notificationSendCount = new ConcurrentHashMap<>();

    // Мапа для хранения времени ПЕРВОЙ отправки каждого напоминания
    // Key: ID напоминания, Value: время первой отправки
    private final Map<Long, LocalDateTime> notificationFirstSentTime = new ConcurrentHashMap<>();

    public NotificationSchedulerService(NotificationTaskRepository repository, TelegramBot telegramBot) {
        this.repository = repository;
        this.telegramBot = telegramBot;
    }

    /**
     * Шедулер, который запускается каждые 10 минут.
     * Ищет напоминания, которые должны быть отправлены в ближайшие 60 минут (текущее время + 1 час)
     * и отправляет их по расписанию: каждые 10 минут в течение 1 часа
     */
    @Scheduled(cron = "0 */10 * * * *") //каждые 10 минут в 0 секунд
    public void sendScheduledNotification() {
        logger.info("Scheduler started at {}", LocalDateTime.now());

        // Обрезаем время до минут
        LocalDateTime currentTime = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        logger.info("Searching notifications for time: {}", currentTime);

        // Время начала поиска - текущее время, округленное до минут
        LocalDateTime searchStartTime = currentTime.truncatedTo(ChronoUnit.MINUTES);

        // Время окончания поиска - через 1 час от текущего времени
        LocalDateTime searchEndTime = searchStartTime.plusHours(1);
        logger.info("Searching notifications between {} and {}",
                searchStartTime, searchEndTime);

        // Ищем все напоминания, которые попадают в диапазон
        List<NotificationTask> notifications = repository.findByNotificationDateTimeBetween(searchStartTime, searchEndTime);
        logger.info("Found {} notifications in time range", notifications.size());

        // Отправляем каждое подходящее напоминание
        int sentCount = 0;
        for (NotificationTask task : notifications) {
            // Проверяем, нужно ли отправлять это напоминание сейчас
            if (shouldSendNow(task, currentTime)) {
                sendNotification(task);
                sentCount++;
            }
        }
        logger.info("Scheduler finished at {}. Sent {} notifications",
                LocalDateTime.now(), sentCount);
    }

    /**
     * Определяет, нужно ли отправлять напоминание в текущий запуск шедулера
     * Логика отправки: первая отправка сразу при наступлении времени,
     * затем повторные каждые 10 минут в течение 1 часа (всего 6 отправок)
     *
     * @param task напоминание для проверки
     * @param currentTime текущее время выполнения шедулера
     * @return true если напоминание нужно отправить сейчас
     */
    private boolean shouldSendNow(NotificationTask task, LocalDateTime currentTime) {
        Long taskId = task.getId();
        LocalDateTime notificationTime = task.getNotificationDateTime();

        //Если время напоминания еще НЕ наступило - не отправляем
        if (notificationTime.isAfter(currentTime)) {
            logger.debug("Notification ID: {} not ready yet (scheduled for {})",
                    taskId, notificationTime);
            return false;
        }
        // Получаем время первой отправки и счетчик
        LocalDateTime firstSentTime = notificationFirstSentTime.get(taskId);
        Integer sendCount = notificationSendCount.getOrDefault(taskId, 0);

        // Пул 1: первая отправка
        if (firstSentTime == null) {
            notificationFirstSentTime.put(taskId, currentTime);
            notificationSendCount.put(taskId, 1);
            logger.info("First send for notification ID: {} (scheduled for {})",
                    taskId, notificationTime);
            return true;
        }

        // Пул 1: Если с момента первой отправки прошло более часа - прекращаем отправки
        if (firstSentTime.plusHours(1).isBefore(currentTime)) {
            logger.info("Notification ID: {} expired (1 hour passed)since first send at {})",
                    taskId, firstSentTime);
            // НЕ удаляем из кэша сразу - оставляем для cleanup метода
            return false;
        }

        // Пул 2: Повторная отправка (если прошло достаточно времени и не превышен лимит)
        if (sendCount < 6 && shouldSendBasedOnInterval(firstSentTime, sendCount, currentTime)) {
            // Увеличиваем счетчик отправок
            notificationSendCount.put(taskId, sendCount + 1);
            logger.info("Resend #{}/6 for notification ID: {} (first sent at {})",
                    sendCount + 1, taskId, firstSentTime);
            return true;
        }

        // Пул 3: Не подходит ни под один критерий отправки
        logger.debug("Notification ID: {} not scheduled for send now (count: {}, first: {})",
                taskId, sendCount, firstSentTime);
        return false;
    }

    /**
     * Определяет, нужно ли отправлять напоминание на основе интервалов
     * Интервалы отправки: 0, 10, 20, 30, 40, 50 минут от времени первой отправки
     *
     * @param firstSentTime время первой отправки
     * @param sendCount количество уже выполненных отправок
     * @param currentTime текущее время
     * @return true если пришло время для очередной отправки
     */
    private boolean shouldSendBasedOnInterval(LocalDateTime firstSentTime, int sendCount, LocalDateTime currentTime) {
        //Рассчитываем время следующей отправки: первая_отправка + (счетчик * 10 минут)
        LocalDateTime nextSendTime = firstSentTime.plusMinutes(sendCount * 10L);

        // Отправляем, если текущее время уже достигло или превысило время следующей отправки
        boolean shouldSend = !currentTime.isBefore(nextSendTime);

        logger.debug("Interval check: firstSent={}, count={}, nextSend={}, current={}, shouldSend={}",
                firstSentTime, sendCount, nextSendTime, currentTime, shouldSend);

        return shouldSend;

    }

    /**
     * Отправляет напоминание пользователю с запросом подтверждения
     * @param task напоминание для отправки
     */
    private void sendNotification(NotificationTask task) {
        try {
            // Форматируем время для красивого отображения
            String formattedTime = task.getNotificationDateTime()
                    .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));

            // Создаем сообщение с форматированием
            String message = "! Напоминание !\n"+
                     task.getNotificationText()+
                    "\n\n Время: " +
                    task.getNotificationDateTime().format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")) +
                    "\n\n Остановить напоминание, отправьте 'Ок' для отключения "+
                    "\n\n ! Напоминание автоматически удалится через 1 час";

            SendMessage sendMessage = new SendMessage(task.getChatId(), message);
            telegramBot.execute(sendMessage);

            logger.info("Notification sent to chat: {}, text: {}, ID: {}",
                    task.getChatId(),
                    task.getNotificationText(),
                    task.getId());

        } catch (Exception e) {
            logger.error("Failed to send notification to chat: {}", task.getChatId(), e);
        }
    }

    /**
     * Очистка старых записей из кэша.
     * Запускается каждый час для удаления устаревших данных
     */
    @Scheduled(cron = "0 0 * * * *") // каждый час в 0 минут
    public void cleanupCache() {
        logger.info("Starting cleanup of old notifications");

        // Напоминания старше 1 часа
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        int initialSize = notificationFirstSentTime.size();

        // Удаляем записи, где первая отправка была более 1 часа назад
        notificationFirstSentTime.entrySet().removeIf(entry -> {
            boolean shouldRemove = entry.getValue().isBefore(oneHourAgo);
            if (shouldRemove) {
                logger.debug("Removing from cache: notification ID: {}", entry.getKey());
            }
            return shouldRemove;
        });

        // Синхронизируем вторую мапу - удаляем записи, которых нет в первой
        notificationSendCount.entrySet().removeIf(entry ->
                !notificationFirstSentTime.containsKey(entry.getKey())
        );

        int removedCount = initialSize - notificationFirstSentTime.size();
        logger.info("Cache cleanup completed. Removed {} entries, current cache size: {}",
                removedCount, notificationFirstSentTime.size());
            }

    /**
     * Очищает выполненные напоминания, у которых закончился период отправок.
     * Удаляем напоминания, у которых первая отправка была более 1 часа назад
     */
    @Scheduled(cron = "0 0 * * * *") // каждый час в 0 минут
    public void cleanupOldNotifications(){
        logger.info("Starting cleanup of of completed notifications");
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        int deletedCount = 0;

        // Ищем напоминания, которые УЖЕ были в кэше (значит, их время наступило)
        // и первая отправка была более 1 часа назад
        for (Map.Entry<Long, LocalDateTime> entry : notificationFirstSentTime.entrySet()) {
            Long taskId = entry.getKey();
            LocalDateTime firstSentTime = entry.getValue();

            if (firstSentTime.isBefore(oneHourAgo)) {
                // Напоминание уже отработало свой час отправок - удаляем из базы
                try {
                    repository.deleteById(taskId);
                    deletedCount++;
                    logger.info(" Deleted completed notification ID: {} (first sent at {})", taskId, firstSentTime);
                } catch (Exception e) {
                    logger.error(" Failed to delete notification ID: {}", taskId, e);
                }
            }
        }
        logger.info("🧹 Cleanup completed. Deleted {} completed notifications", deletedCount);
    }

    /**
     * Получает количество закэшированных напоминаний (для отладки)
     * @return количество активных напоминаний в кэше
     */
    public int getCacheSize(){
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

}

