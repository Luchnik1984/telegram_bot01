package pro.sky.telegrambot.service;

import pro.sky.telegrambot.model.Chat;
import pro.sky.telegrambot.model.NotificationInstance;
import pro.sky.telegrambot.model.NotificationTemplate;
import pro.sky.telegrambot.model.enums.NotificationStatus;
import pro.sky.telegrambot.repository.NotificationInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Transactional
public class NotificationInstanceService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationInstanceService.class);

    private final NotificationInstanceRepository instanceRepository;
    private final NotificationTemplateService templateService;
    private final NotificationSendingStateService sendingStateService;

    public NotificationInstanceService(NotificationInstanceRepository instanceRepository,
                                       NotificationTemplateService templateService,
                                       NotificationSendingStateService sendingStateService) {
        this.instanceRepository = instanceRepository;
        this.templateService = templateService;
        this.sendingStateService = sendingStateService;
    }

    /**
     * Создать экземпляр напоминания
     */
    public NotificationInstance createInstance(Chat chat, String notificationText,
                                               LocalDateTime scheduledTime) {
        logger.info("Creating notification instance for chat: {}, scheduled: {}",
                chat.getId(), scheduledTime);

        // Создаем или находим шаблон
        NotificationTemplate template = templateService.createOrFindTemplate(chat, notificationText);

        // Создаем экземпляр
        NotificationInstance instance = new NotificationInstance(chat, template,
                notificationText, scheduledTime);
        NotificationInstance savedInstance = instanceRepository.save(instance);

        // Инициализируем состояние отправки
        sendingStateService.initializeState(savedInstance);

        logger.info("Created notification instance: {}", savedInstance.getId());
        return savedInstance;
    }

    /**
     * Создать экземпляр без шаблона (для уникальных напоминаний)
     */
    public NotificationInstance createInstanceWithoutTemplate(Chat chat, String notificationText,
                                                              LocalDateTime scheduledTime) {
        logger.info("Creating notification instance without template for chat: {}, scheduled: {}",
                chat.getId(), scheduledTime);

        NotificationInstance instance = new NotificationInstance(chat, notificationText, scheduledTime);
        NotificationInstance savedInstance = instanceRepository.save(instance);

        // Инициализируем состояние отправки
        sendingStateService.initializeState(savedInstance);

        logger.info("Created notification instance without template: {}", savedInstance.getId());
        return savedInstance;
    }

    /**
     * Найти экземпляры для отправки в текущее время
     */
    public List<NotificationInstance> findDueInstances(LocalDateTime currentTime) {
        LocalDateTime oneHourAgo = currentTime.minusHours(1);
        return instanceRepository.findDueInstances(currentTime, oneHourAgo);
    }

    /**
     * Найти экземпляры для текущей минуты
     */
    public List<NotificationInstance> findInstancesForCurrentMinute(LocalDateTime currentTime) {
        LocalDateTime oneHourAgo = currentTime.minusHours(1);
        return instanceRepository.findInstancesForCurrentMinute(currentTime, oneHourAgo);
    }

    /**
     * Найти последнее активное напоминание для чата
     */
    public Optional<NotificationInstance> findLastActiveByChatId(Long chatId) {
        return instanceRepository.findLastActiveByChatId(chatId);
    }

    /**
     * Обновить статус экземпляра
     */
    public void updateStatus(Long instanceId, NotificationStatus status) {
        instanceRepository.updateStatus(instanceId, status);
        logger.info("Updated instance {} status to: {}", instanceId, status);
    }

    /**
     * Пометить как выполненное
     */
    public void markAsCompleted(Long instanceId) {
        updateStatus(instanceId, NotificationStatus.COMPLETED);
        sendingStateService.completeSending(instanceId);
    }

    /**
     * Пометить как истекшее
     */
    public void markAsExpired(Long instanceId) {
        updateStatus(instanceId, NotificationStatus.EXPIRED);
        sendingStateService.completeSending(instanceId);
    }

    /**
     * Отменить напоминание
     */
    public void cancelInstance(Long instanceId) {
        updateStatus(instanceId, NotificationStatus.CANCELLED);
        sendingStateService.completeSending(instanceId);
    }

    /**
     * Получить все экземпляры для чата
     */
    public List<NotificationInstance> getInstancesByChat(Chat chat) {
        return instanceRepository.findByChatOrderByScheduledTimeDesc(chat);
    }

    /**
     * Получить активные экземпляры для чата
     */
    public List<NotificationInstance> getActiveInstancesByChat(Chat chat) {
        return instanceRepository.findByChatAndStatusOrderByScheduledTimeDesc(
                chat, NotificationStatus.ACTIVE);
    }

    /**
     * Очистить старые завершенные экземпляры
     * ИСПРАВЛЕННАЯ ВЕРСИЯ
     */
    public void cleanupOldInstances() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusDays(7); // Храним 7 дней

        try {
            // Находим экземпляры для удаления
            List<NotificationInstance> oldInstances = instanceRepository.findByCreatedAtBefore(cutoffTime);

            // Фильтруем только завершенные, истекшие и отмененные
            List<NotificationInstance> instancesToDelete = oldInstances.stream()
                    .filter(instance ->
                            instance.getStatus() == NotificationStatus.COMPLETED ||
                                    instance.getStatus() == NotificationStatus.EXPIRED ||
                                    instance.getStatus() == NotificationStatus.CANCELLED)
                    .collect(Collectors.toList());

            if (!instancesToDelete.isEmpty()) {
                // Удаляем найденные экземпляры
                instanceRepository.deleteAll(instancesToDelete);
                logger.info("Cleaned up {} old instances", instancesToDelete.size());

                // Логируем детали удаления для отладки
                for (NotificationInstance instance : instancesToDelete) {
                    logger.debug("Deleted old instance: ID={}, Created={}, Status={}, Text={}",
                            instance.getId(), instance.getCreatedAt(),
                            instance.getStatus(), instance.getNotificationText());
                }
            } else {
                logger.debug("No old instances to clean up (cutoff time: {})", cutoffTime);
            }

        } catch (Exception e) {
            logger.error("Error during cleanup of old instances", e);
        }
    }

    /**
     * Найти экземпляр по ID
     */
    public Optional<NotificationInstance> findById(Long instanceId) {
        return instanceRepository.findById(instanceId);
    }

    /**
     * Получить статистику по экземплярам
     */
    public String getInstancesStatistics() {
        long total = instanceRepository.count();
        long pending = instanceRepository.findByStatus(NotificationStatus.PENDING).size();
        long active = instanceRepository.findByStatus(NotificationStatus.ACTIVE).size();
        long completed = instanceRepository.findByStatus(NotificationStatus.COMPLETED).size();
        long expired = instanceRepository.findByStatus(NotificationStatus.EXPIRED).size();

        return String.format(
                " Статистика экземпляров:\n" +
                        "• Всего: %d\n" +
                        "• Ожидают отправки: %d\n" +
                        "• Активные: %d\n" +
                        "• Выполненные: %d\n" +
                        "• Истекшие: %d",
                total, pending, active, completed, expired
        );
    }
}

