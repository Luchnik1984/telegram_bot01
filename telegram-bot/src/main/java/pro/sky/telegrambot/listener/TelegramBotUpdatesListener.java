package pro.sky.telegrambot.listener;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.model.NotificationTask;
import pro.sky.telegrambot.model.ParseResult;
import pro.sky.telegrambot.repository.NotificationTaskRepository;
import pro.sky.telegrambot.service.NotificationSchedulerService;
import pro.sky.telegrambot.service.NotificationTaskService;


import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Optional;


@Service
public class TelegramBotUpdatesListener implements UpdatesListener {

    private final Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);

    @Autowired
    private TelegramBot telegramBot;
    @Autowired
    private NotificationTaskService notificationTaskService;
    @Autowired
    private NotificationTaskRepository notificationTaskRepository;
    @Autowired
    private NotificationSchedulerService notificationSchedulerService;

    // Шаблон правильного формата сообщения для напоминания
    private final String correctMassageForm =
            " **Правильный формат напоминания:**\n" +
                    "дд.мм.гггг чч:мм Текст напоминания`\n\n" +
                    "**Пример:**\n" +
                    "24.01.2025 10:00 Сдать домашнюю работу`\n\n" +
                    "**Важно:** \n" +
                    "Дата и время должны быть в будущем! \n"+
                    "Текст напоминания не может быть пустым\n" +
                    "Напоминание автоматически удалится через 1 час";


    /**
     * Инициализация бота после создания бина.
     * Устанавливаем этого класса как обработчика обновлений
     */
    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
        logger.info(" Telegram Bot Updates Listener initialized");
    }

    /**
     * Основной метод обработки входящих сообщений от Telegram
     * @param updates список обновлений от Telegram API
     * @return константа подтверждения обработки обновлений
     */
    @Override
    public int process(List<Update> updates) {
        logger.info("Received {} update(s)", updates.size());

        // Обрабатываем каждое обновление в списке
        updates.forEach(update -> {
            logger.info("Processing update: {}", update);

            // Проверяем, что сообщение не пустое и содержит текст
            if (isValidMessage(update)) {
                String messageText = update.message().text();
                Long chatId = update.message().chat().id();
                String userName = update.message().chat().firstName(); // Имя пользователя для персонализации

                logger.info("User: {} (Chat ID: {}) sent: '{}'", userName, chatId, messageText);

                // Обрабатываем команду /start
                if ("/start".equals(messageText)) {
                    handleStartCommand(chatId, userName);
                }
                // Обрабатываем подтверждение "Ок" для последнего напоминания
                else if (isConfirmationCommand(messageText)) {
                    handleLastNotificationConfirmation(chatId, userName);
                }
                // Пытаемся обработать как напоминание
                else {
                    handleNotificationCreation(messageText, chatId, userName);
                }
            } else {
                logger.warn("️Invalid message received in update: {}", update);
            }
        });

        // Подтверждаем обработку всех обновлений
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    /**
     * Проверяет валидность входящего сообщения
     * @param update обновление от Telegram
     * @return true если сообщение можно обработать
     */
    private boolean isValidMessage(Update update) {
        return update != null &&
                update.message() != null &&
                update.message().text() != null &&
                !update.message().text().trim().isEmpty();
    }

    /**
     * Обрабатывает команду /start - приветствие пользователя
     * @param chatId идентификатор чата
     * @param userName имя пользователя для персонализации
     */
    private void handleStartCommand(Long chatId, String userName) {
        String welcomeMessage = String.format(
                " Привет, %s! Я твой бот-напоминатель! \n\n" +
                        "Я помогу тебе не забывать о важных делах.\n\n" +
                        " **Что я умею:**\n" +
                        "• Создавать напоминания на любое время\n" +
                        "• Присылать уведомления каждые 10 минут в течение часа\n" +
                        "• Позволять отключать напоминания когда они выполнены\n\n" +
                        "%s\n\n" +
                        " **Подсказка:** После создания напоминания я буду присылать его 6 раз " +
                        "с интервалом 10 минут. Для остановки напоминания - просто ответь 'Ок'!",
                userName != null ? userName : "друг", correctMassageForm
        );

        sendMessage(chatId, welcomeMessage);
        logger.info(" Sent welcome message to user: {} (Chat ID: {})", userName, chatId);
    }

    /**
     * Обрабатывает подтверждение выполнения напоминания (команды "Ок", "Готово" и т.д.)
     * @param chatId идентификатор чата
     * @param userName имя пользователя для персонализации
     */
    private void handleLastNotificationConfirmation(Long chatId, String userName) {
        logger.info(" Handling confirmation from user: {} (Chat ID: {})", userName, chatId);

        try {
            // Получаем ID последнего отправленного напоминания для этого чата
            Long lastNotificationId = notificationSchedulerService.getLastSentNotificationId(chatId);

            // Проверяем, есть ли активное напоминание
            if (lastNotificationId == null) {
                sendMessage(chatId, "📭 У вас нет активных напоминаний для подтверждения.\n" +
                        "Сначала создайте напоминание, и когда оно придет - ответьте 'Ок'!");
                logger.warn(" No active notification found for chat: {}", chatId);
                return;
            }

            logger.info("Found last notification ID: {} for chat: {}", lastNotificationId, chatId);

            // Ищем напоминание в базе данных по ID
            Optional<NotificationTask> notificationOptional = notificationTaskRepository.findById(lastNotificationId);

            // Проверяем, существует ли напоминание
            if (notificationOptional.isEmpty()) {
                sendMessage(chatId, "Напоминание не найдено. Возможно, оно уже было удалено.");
                logger.warn("Notification not found in database: ID {}", lastNotificationId);

                // Очищаем устаревший ID из кэша
                notificationSchedulerService.clearLastSentNotification(chatId);
                return;
            }

            NotificationTask notification = notificationOptional.get();

            // Дополнительная проверка безопасности: убеждаемся, что напоминание принадлежит этому пользователю
            if (!notification.getChatId().equals(chatId)) {
                sendMessage(chatId, " Это не ваше напоминание! Вы не можете его подтвердить.");
                logger.error("Security issue: User {} tried to confirm notification {} belonging to another user",
                        chatId, notification.getId());

                // Очищаем некорректный ID из кэша
                notificationSchedulerService.clearLastSentNotification(chatId);
                return;
            }

            // Сохраняем текст напоминания для сообщения пользователю
            String notificationText = notification.getNotificationText();

            // УДАЛЯЕМ напоминание из базы данных
            notificationTaskRepository.delete(notification);

            // Очищаем информацию о последнем отправленном напоминании из кэша
            notificationSchedulerService.clearLastSentNotification(chatId);

            // Отправляем подтверждение пользователю
            String confirmationMessage = String.format(
                    "**Напоминание выполнено и удалено!** \n\n" +
                            "**Текст:** %s\n\n" +
                            "Отлично done! Если нужно новое напоминание - просто создайте его!",
                    notificationText
            );

            sendMessage(chatId, confirmationMessage);
            logger.info("️Successfully deleted notification ID: {} for user: {} (Chat ID: {})",
                    lastNotificationId, userName, chatId);

        } catch (Exception e) {
            logger.error("Error handling confirmation for chat: {}", chatId, e);
            sendMessage(chatId, "Произошла ошибка при обработке подтверждения. Попробуйте еще раз.");
        }
    }

    /**
     * Обрабатывает создание нового напоминания
     * @param messageText текст сообщения от пользователя
     * @param chatId идентификатор чата
     * @param userName имя пользователя для персонализации
     */
    private void handleNotificationCreation(String messageText, Long chatId, String userName) {
        logger.info("Attempting to create notification for user: {} (Chat ID: {})", userName, chatId);

        // Пытаемся распарсить и сохранить напоминание
        ParseResult result = notificationTaskService.parseAndSaveNotification(messageText, chatId);

        if (result.isSuccess()) {
            // Успешное создание напоминания
            NotificationTask savedTask = result.getTask();
            String successMessage = String.format(
                    " **Напоминание успешно создано!** \n\n" +
                            " **Текст:** %s\n" +
                            " **Время:** %s\n\n" +
                            "Я буду присылать вам это напоминание каждые 10 минут в течение часа, " +
                            "начиная с указанного времени. Для остановки - просто ответьте 'Ок'!",
                    savedTask.getNotificationText(),
                    savedTask.getNotificationDateTime().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
            );

            sendMessage(chatId, successMessage);
            logger.info(" Notification created successfully for user: {} (Chat ID: {}), Task ID: {}",
                    userName, chatId, savedTask.getId());

        } else {
            // Ошибка при создании напоминания
            String errorMessage = result.getErrorMessage();
            String detailedErrorMessage = String.format(
                    "**Ошибка создания напоминания:** %s\n\n%s",
                    errorMessage, correctMassageForm
            );

            sendMessage(chatId, detailedErrorMessage);
            logger.warn("️Failed to create notification for user: {} (Chat ID: {}). Error: {}",
                    userName, chatId, errorMessage);
        }
    }

    /**
     * Проверяет, является ли сообщение командой подтверждения.
     * Поддерживает различные варианты написания
     * @param messageText текст сообщения
     * @return true если это команда подтверждения
     */
    private boolean isConfirmationCommand(String messageText) {
        if (messageText == null) return false;

        // Приводим к нижнему регистру и удаляем пробелы для надежного сравнения
        String trimmedText = messageText.trim().toLowerCase();

        // Список поддерживаемых команд подтверждения
        return trimmedText.equals("ок") ||
                trimmedText.equals("ok") ||
                trimmedText.equals("готово") ||
                trimmedText.equals("сделал") ||
                trimmedText.equals("выполнил") ||
                trimmedText.equals("done") ||
                trimmedText.equals("сделано") ||
                trimmedText.equals("выполнено") ||
                trimmedText.equals("готов") ||
                trimmedText.equals("убрать") ||
                trimmedText.equals("удалить");
    }

    /**
     * Метод для отправки сообщения в Telegram
     * @param chatId идентификатор чата
     * @param message текст сообщения
     */
    private void sendMessage(Long chatId, String message) {
        try {
            SendMessage sendMessage = new SendMessage(chatId, message);
            telegramBot.execute(sendMessage);
            logger.debug("Sent message to chat: {}, length: {} chars", chatId, message.length());
        } catch (Exception e) {
            logger.error("Failed to send message to chat: {}", chatId, e);
        }
    }
}