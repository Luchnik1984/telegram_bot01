package pro.sky.telegrambot.service;

import pro.sky.telegrambot.model.Chat;
import pro.sky.telegrambot.model.NotificationInstance;
import pro.sky.telegrambot.model.NotificationSendingState;
import pro.sky.telegrambot.model.enums.NotificationStatus;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.response.SendResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;


@Service
@Transactional
public class NotificationOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationOrchestratorService.class);

    private final TelegramBot telegramBot;
    private final ChatService chatService;
    private final NotificationInstanceService instanceService;
    private final NotificationSendingStateService sendingStateService;
    private final NotificationDeliveryService deliveryService;

    public NotificationOrchestratorService(TelegramBot telegramBot,
                                           ChatService chatService,
                                           NotificationInstanceService instanceService,
                                           NotificationSendingStateService sendingStateService,
                                           NotificationDeliveryService deliveryService) {
        this.telegramBot = telegramBot;
        this.chatService = chatService;
        this.instanceService = instanceService;
        this.sendingStateService = sendingStateService;
        this.deliveryService = deliveryService;
    }

    /**
     * Создать напоминание
     */
    public NotificationInstance createReminder(Long chatId, String username, String firstName,
                                               String notificationText, LocalDateTime scheduledTime) {
        logger.info("Creating reminder for chat: {}, text: {}, scheduled: {}",
                chatId, notificationText, scheduledTime);

        // Находим или создаем чат
        Chat chat = chatService.findOrCreateChat(chatId, username, firstName);

        // Создаем экземпляр напоминания
        return instanceService.createInstance(chat, notificationText, scheduledTime);
    }

    /**
     * Обработать напоминания для отправки
     */
    public void processDueNotifications() {
        LocalDateTime currentTime = LocalDateTime.now();
        logger.info("Processing due notifications at: {}", currentTime);

        // Находим экземпляры для текущей минуты
        List<NotificationInstance> dueInstances = instanceService.findInstancesForCurrentMinute(currentTime);

        logger.info("Found {} due instances", dueInstances.size());

        int sentCount = 0;
        for (NotificationInstance instance : dueInstances) {
            if (shouldSendNow(instance, currentTime)) {
                sendNotification(instance);
                sentCount++;
            }
        }

        // Обновляем расписание для повторных отправок
        sendingStateService.updateNextScheduledSends();

        // Очищаем старые экземпляры
        instanceService.cleanupOldInstances();

        logger.info("Processed due notifications. Sent: {}", sentCount);
    }

    /**
     * Определить, нужно ли отправлять напоминание сейчас
     */
    private boolean shouldSendNow(NotificationInstance instance, LocalDateTime currentTime) {
        // Проверяем статус
        if (instance.getStatus() != NotificationStatus.PENDING &&
                instance.getStatus() != NotificationStatus.ACTIVE) {
            return false;
        }

        // Проверяем время создания (не старше 1 часа)
        if (instance.getCreatedAt().plusHours(1).isBefore(currentTime)) {
            instanceService.markAsExpired(instance.getId());
            return false;
        }

        // Проверяем состояние отправки
        if (!sendingStateService.canSend(instance)) {
            return false;
        }

        // Для первой отправки проверяем точное время
        NotificationSendingState state = sendingStateService.findByInstance(instance)
                .orElse(null);

        if (state == null) {
            logger.warn("No sending state found for instance: {}", instance.getId());
            return false;
        }

        if (state.getFirstSentTime() == null) {
            // Первая отправка - проверяем точное время
            LocalDateTime scheduledMinute = instance.getScheduledTime().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            LocalDateTime currentMinute = currentTime.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            return scheduledMinute.equals(currentMinute);
        } else {
            // Повторная отправка - проверяем следующее запланированное время
            if (state.getNextScheduledSend() == null) {
                return false;
            }
            LocalDateTime nextSendMinute = state.getNextScheduledSend().truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            LocalDateTime currentMinute = currentTime.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            return nextSendMinute.equals(currentMinute);
        }
    }

    /**
     * Отправить напоминание
     */
    private void sendNotification(NotificationInstance instance) {
        long startTime = System.currentTimeMillis();

        try {
            // Получаем состояние отправки - исправленная строка:
            NotificationSendingState state = sendingStateService.findByInstanceId(instance.getId())
                    .orElseThrow(() -> new IllegalStateException("No sending state found for instance: "
                            + instance.getId()));

            // Форматируем сообщение
            String message = formatNotificationMessage(instance, state);

            // Отправляем через Telegram Bot API
            SendMessage sendMessage = new SendMessage(instance.getChat().getChatId(), message);
            SendResponse response = telegramBot.execute(sendMessage);

            long deliveryDuration = System.currentTimeMillis() - startTime;

            if (response.isOk()) {
                // Успешная отправка
                deliveryService.recordSuccessfulDelivery(
                        instance,
                        state.getSendCount() + 1,
                        (long) response.message().messageId(),
                        (int) deliveryDuration
                );

                // Обновляем состояние отправки
                sendingStateService.updateAfterSend(instance.getId(), true);

                // Обновляем статус экземпляра
                if (instance.getStatus() == NotificationStatus.PENDING) {
                    instanceService.updateStatus(instance.getId(), NotificationStatus.ACTIVE);
                }

                logger.info("Successfully sent notification to chat: {}, instance: {}, attempt: {}",
                        instance.getChat().getChatId(), instance.getId(), state.getSendCount() + 1);

            } else {
                // Ошибка отправки
                deliveryService.recordFailedDelivery(
                        instance,
                        state.getSendCount() + 1,
                        response.description()
                );

                logger.error("Failed to send notification to chat: {}, error: {}",
                        instance.getChat().getChatId(), response.description());
            }

        } catch (Exception e) {
            long deliveryDuration = System.currentTimeMillis() - startTime;

            // Исправленный вызов - получаем актуальный счетчик отправок:
            Integer attemptNumber = sendingStateService.findByInstanceId(instance.getId())
                    .map(state -> state.getSendCount() + 1)
                    .orElse(1);

            deliveryService.recordFailedDelivery(instance, attemptNumber, e.getMessage());

            logger.error("Exception while sending notification to chat: {}",
                    instance.getChat().getChatId(), e);
        }
    }

    /**
     * Публичный метод для отправки напоминания (для шедулера)
     */
    public void processNotificationForScheduler(NotificationInstance instance) {
        logger.debug("Scheduler requesting notification send for instance: {}", instance.getId());
        sendNotification(instance);
    }

    /**
     * Форматировать сообщение напоминания
     */
    private String formatNotificationMessage(NotificationInstance instance, NotificationSendingState state) {
        String formattedTime = instance.getScheduledTime()
                .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"));

        String repeatInfo = "";
        if (state.getSendCount() > 0) {
            repeatInfo = "\nПовторная отправка #" + (state.getSendCount() + 1) + "/" + state.getMaxAttempts();
        }

        int remainingAttempts = state.getMaxAttempts() - (state.getSendCount() + 1);
        String remainingInfo = remainingAttempts > 0 ?
                "\nОсталось отправок: " + remainingAttempts :
                "\nПоследнее напоминание!";

        return " Напоминание!" + repeatInfo +
                "\n\n" + instance.getNotificationText() +
                "\n\n Время: " + formattedTime +
                remainingInfo +
                "\n\nЧтобы остановить напоминание, отправьте 'Ок'" +
                "\nЧтобы приостановить на 1 час, отправьте 'Пауза'" +
                "\n\nНапоминание автоматически удалится через 1 час после создания";
    }

    /**
     * Подтвердить выполнение напоминания (команда "Ок")
     */
    public boolean confirmCompletion(Long chatId) {
        logger.info("Processing completion confirmation for chat: {}", chatId);

        Optional<NotificationInstance> lastActive = instanceService.findLastActiveByChatId(chatId);

        if (lastActive.isPresent()) {
            NotificationInstance instance = lastActive.get();
            instanceService.markAsCompleted(instance.getId());

            // Отправляем подтверждение пользователю
            String confirmationMessage = "Напоминание выполнено и отключено!\n\n" +
                    "Текст: " + instance.getNotificationText() +
                    "\n\nОтлично! Если нужно новое напоминание - просто создайте его!";

            SendMessage message = new SendMessage(chatId, confirmationMessage);
            telegramBot.execute(message);

            logger.info("Confirmed completion for instance: {}, chat: {}",
                    instance.getId(), chatId);
            return true;
        } else {
            // Нет активных напоминаний
            String message = "У вас нет активных напоминаний для подтверждения.\n" +
                    "Сначала создайте напоминание, и когда оно придет - ответьте 'Ок'!";

            SendMessage sendMessage = new SendMessage(chatId, message);
            telegramBot.execute(sendMessage);

            logger.info("No active instances found for chat: {}", chatId);
            return false;
        }
    }

    /**
     * Поставить напоминание на паузу
     */
    public boolean pauseReminder(Long chatId) {
        logger.info("Processing pause request for chat: {}", chatId);

        Optional<NotificationInstance> lastActive = instanceService.findLastActiveByChatId(chatId);

        if (lastActive.isPresent()) {
            NotificationInstance instance = lastActive.get();
            LocalDateTime pauseUntil = LocalDateTime.now().plusHours(1);

            sendingStateService.pauseSending(instance.getId(), pauseUntil);

            // Отправляем подтверждение пользователю
            String pauseMessage = "Напоминание приостановлено на 1 час!\n\n" +
                    "Текст: " + instance.getNotificationText() +
                    "\n\nОтправки возобновятся автоматически в " +
                    pauseUntil.format(DateTimeFormatter.ofPattern("HH:mm")) +
                    "\n\nЧтобы возобновить сразу, отправьте 'Возобновить'";

            SendMessage message = new SendMessage(chatId, pauseMessage);
            telegramBot.execute(message);

            logger.info("Paused instance: {} for chat: {} until {}",
                    instance.getId(), chatId, pauseUntil);
            return true;
        } else {
            String message = "Нет активных напоминаний для приостановки.";
            SendMessage sendMessage = new SendMessage(chatId, message);
            telegramBot.execute(sendMessage);

            logger.info("No active instances found for pause request, chat: {}", chatId);
            return false;
        }
    }

    /**
     * Возобновить напоминание
     */
    public boolean resumeReminder(Long chatId) {
        logger.info("Processing resume request for chat: {}", chatId);

        Optional<NotificationInstance> lastActive = instanceService.findLastActiveByChatId(chatId);

        if (lastActive.isPresent()) {
            NotificationInstance instance = lastActive.get();

            sendingStateService.resumeSending(instance.getId());

            String resumeMessage = "Напоминание возобновлено!\n\n" +
                    "Текст: " + instance.getNotificationText() +
                    "\n\nСледующая отправка через 10 минут.";

            SendMessage message = new SendMessage(chatId, resumeMessage);
            telegramBot.execute(message);

            logger.info("Resumed instance: {} for chat: {}", instance.getId(), chatId);
            return true;
        } else {
            String message = "Нет приостановленных напоминаний для возобновления.";
            SendMessage sendMessage = new SendMessage(chatId, message);
            telegramBot.execute(sendMessage);

            logger.info("No paused instances found for resume request, chat: {}", chatId);
            return false;
        }
    }

    /**
     * Получить статистику для пользователя
     */
    public void sendStatistics(Long chatId) {
        Optional<Chat> chatOpt = chatService.findByChatId(chatId);

        if (chatOpt.isPresent()) {
            Chat chat = chatOpt.get();

            // Получаем статистику
            List<NotificationInstance> instances = instanceService.getInstancesByChat(chat);
            long total = instances.size();
            long completed = instances.stream()
                    .filter(i -> i.getStatus() == NotificationStatus.COMPLETED)
                    .count();
            long active = instances.stream()
                    .filter(i -> i.getStatus() == NotificationStatus.ACTIVE)
                    .count();

            double avgDeliveryTime = deliveryService.getAverageDeliveryTime(chatId);

            String statsMessage = String.format(
                    "Ваша статистика:\n\n" +
                            "Всего напоминаний: %d\n" +
                            "Выполнено: %d\n" +
                            "Активных: %d\n" +
                            "Среднее время доставки: %.0f мс\n\n" +
                            "Продолжайте в том же духе! ",
                    total, completed, active, avgDeliveryTime
            );

            SendMessage message = new SendMessage(chatId, statsMessage);
            telegramBot.execute(message);

            logger.info("Sent statistics to chat: {}", chatId);
        } else {
            String message = "Статистика недоступна. Сначала создайте напоминание!";
            SendMessage sendMessage = new SendMessage(chatId, message);
            telegramBot.execute(sendMessage);
        }
    }
}
