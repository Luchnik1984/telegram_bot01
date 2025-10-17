package pro.sky.telegrambot.listener;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.model.ParseResult;
import pro.sky.telegrambot.service.NotificationOrchestratorService;
import pro.sky.telegrambot.service.NotificationTaskService;

import javax.annotation.PostConstruct;
import java.util.List;


@Service
public class TelegramBotUpdatesListener implements UpdatesListener {

    private final Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);

    private final TelegramBot telegramBot;
    private final NotificationOrchestratorService orchestratorService;
    private final NotificationTaskService notificationTaskService;

    // ШАБЛОН ПРАВИЛЬНОГО ФОРМАТА СООБЩЕНИЯ
    private final String correctMessageFormat =
            " **Правильный формат напоминания:**\n" +
                    "`дд.мм.гггг чч:мм Текст напоминания`\n\n" +
                    "**Пример:**\n" +
                    "`25.12.2024 15:30 Сдать домашнюю работу`\n\n" +
                    "**Важно:** \n" +
                    "• Дата и время должны быть в будущем!\n" +
                    "• Текст напоминания не может быть пустым\n" +
                    "• Напоминание автоматически удалится через 1 час";

    public TelegramBotUpdatesListener(TelegramBot telegramBot,
                                      NotificationOrchestratorService orchestratorService,
                                      NotificationTaskService notificationTaskService) {
        this.telegramBot = telegramBot;
        this.orchestratorService = orchestratorService;
        this.notificationTaskService = notificationTaskService;

        logger.info("TelegramBotUpdatesListener initialized with both old and new services");
    }

    /**
     * ИНИЦИАЛИЗАЦИЯ БОТА ПОСЛЕ СОЗДАНИЯ БИНА
     */
    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
        logger.info(" Telegram Bot Updates Listener initialized and ready");
    }

    /**
     * ОСНОВНОЙ МЕТОД ОБРАБОТКИ ВХОДЯЩИХ СООБЩЕНИЙ
     */
    @Override
    public int process(List<Update> updates) {
        logger.info(" Received {} update(s)", updates.size());

        try {
            // ОБРАБОТКА КАЖДОГО ОБНОВЛЕНИЯ
            updates.forEach(update -> {
                logger.debug("Processing update: {}", update.updateId());

                if (!isValidMessage(update)) {
                    logger.warn(" Invalid message received in update: {}", update);
                    return;
                }

                String messageText = update.message().text().trim();
                Long chatId = update.message().chat().id();
                String userName = update.message().chat().firstName();
                String username = update.message().chat().username();

                logger.info("👤 User: {} (@{}, Chat ID: {}) sent: '{}'",
                        userName, username, chatId, messageText);

                try {
                    // ОБРАБОТКА КОМАНД БОТА
                    processUserMessage(messageText, chatId, userName, username);

                } catch (Exception e) {
                    logger.error(" Error processing message from chat {}: {}", chatId, messageText, e);
                    sendErrorMessage(chatId, "Произошла непредвиденная ошибка. Попробуйте еще раз.");
                }
            });

            logger.info(" Successfully processed all {} updates", updates.size());
            return UpdatesListener.CONFIRMED_UPDATES_ALL;

        } catch (Exception e) {
            logger.error(" Critical error processing updates batch", e);
            return UpdatesListener.CONFIRMED_UPDATES_ALL; // Все равно подтверждаем обработку
        }
    }

    /**
     * ОБРАБОТКА СООБЩЕНИЯ ПОЛЬЗОВАТЕЛЯ
     */
    private void processUserMessage(String messageText, Long chatId, String userName, String username) {
        // ОБРАБОТКА СТАНДАРТНЫХ КОМАНД
        if ("/start".equals(messageText)) {
            handleStartCommand(chatId, userName);
        }
        else if ("/help".equals(messageText) || "/помощь".equals(messageText)) {
            handleHelpCommand(chatId);
        }
        else if ("/stats".equals(messageText) || "/статистика".equals(messageText)) {
            handleStatsCommand(chatId);
        }
        // ОБРАБОТКА КОМАНД УПРАВЛЕНИЯ НАПОМИНАНИЯМИ
        else if (isConfirmationCommand(messageText)) {
            handleConfirmationCommand(chatId, userName);
        }
        else if (isPauseCommand(messageText)) {
            handlePauseCommand(chatId);
        }
        else if (isResumeCommand(messageText)) {
            handleResumeCommand(chatId);
        }
        // ОБРАБОТКА СОЗДАНИЯ НАПОМИНАНИЯ (совместимость со старой и новой архитектурой)
        else {
            handleNotificationCreation(messageText, chatId, userName, username);
        }
    }

    /**
     * ОБРАБОТКА КОМАНДЫ /start
     */
    private void handleStartCommand(Long chatId, String userName) {
        String welcomeMessage = String.format(
                " Привет, %s! Я твой умный бот-напоминатель! \n\n" +
                        "Я помогу тебе не забывать о важных делах и буду напоминать о них несколько раз.\n\n" +
                        " **Что я умею:**\n" +
                        "• Создавать напоминания на любое время\n" +
                        "• Присылать уведомления 6 раз с интервалом 10 минут\n" +
                        "• Позволять подтверждать выполнение командой 'Ок'\n" +
                        "• Приостанавливать напоминания на 1 час\n" +
                        "• Показывать статистику ваших напоминаний\n\n" +
                        "%s\n\n" +
                        " **Подсказка:** После создания напоминания я буду присылать его 6 раз " +
                        "с интервалом 10 минут. Для остановки - просто ответь 'Ок'!\n\n" +
                        " Для просмотра статистики отправьте /stats\n" +
                        " Для помощи отправьте /help",
                userName != null ? userName : "друг", correctMessageFormat
        );

        sendMessage(chatId, welcomeMessage);
        logger.info(" Sent welcome message to user: {} (Chat ID: {})", userName, chatId);
    }

    /**
     * ОБРАБОТКА КОМАНДЫ /help
     */
    private void handleHelpCommand(Long chatId) {
        String helpMessage =
                " **Помощь по командам:**\n\n" +
                        " **Создание напоминания:**\n" +
                        correctMessageFormat + "\n\n" +

                        " **Управление напоминаниями:**\n" +
                        "• `Ок`, `Готово`, `Сделал` - подтвердить выполнение и остановить напоминание\n" +
                        "• `Пауза`, `Приостановить` - поставить напоминание на паузу на 1 час\n" +
                        "• `Возобновить`, `Продолжить` - возобновить отправку напоминания\n\n" +

                        " **Команды бота:**\n" +
                        "• `/start` - начать работу с ботом\n" +
                        "• `/stats` или `/статистика` - посмотреть статистику\n" +
                        "• `/help` или `/помощь` - показать эту справку\n\n" +

                        "️ **Как это работает:**\n" +
                        "1. Создаете напоминание в правильном формате\n" +
                        "2. В указанное время получаете первое уведомление\n" +
                        "3. Далее получаете еще 5 напоминаний каждые 10 минут\n" +
                        "4. Когда задача выполнена - отправляете 'Ок'\n" +
                        "5. Напоминание автоматически удаляется через 1 час\n\n" +

                        " Удачи в использовании!";

        sendMessage(chatId, helpMessage);
        logger.info(" Sent help message to chat: {}", chatId);
    }

    /**
     * ОБРАБОТКА КОМАНДЫ /stats
     */
    private void handleStatsCommand(Long chatId) {
        // ИСПОЛЬЗУЕМ СТАРЫЙ СЕРВИС ДЛЯ СТАТИСТИКИ (обратная совместимость)
        String statistics = notificationTaskService.getChatStatistics(chatId);
        sendMessage(chatId, statistics);
        logger.info(" Sent statistics to chat: {}", chatId);
    }

    /**
     * ОБРАБОТКА КОМАНД ПОДТВЕРЖДЕНИЯ (Ок, Готово и т.д.)
     */
    private void handleConfirmationCommand(Long chatId, String userName) {
        logger.info(" Processing confirmation from user: {} (Chat ID: {})", userName, chatId);

        // ИСПОЛЬЗУЕМ НОВЫЙ ORCHESTRATOR ДЛЯ ОБРАБОТКИ ПОДТВЕРЖДЕНИЯ
        boolean success = orchestratorService.confirmCompletion(chatId);

        if (success) {
            logger.info(" Successfully confirmed completion for chat: {}", chatId);
        } else {
            logger.info(" No active instances to confirm for chat: {}", chatId);
        }
    }

    /**
     * ОБРАБОТКА КОМАНДЫ ПАУЗЫ
     */
    private void handlePauseCommand(Long chatId) {
        logger.info(" Processing pause request for chat: {}", chatId);

        // ИСПОЛЬЗУЕМ НОВЫЙ ORCHESTRATOR ДЛЯ ПАУЗЫ
        boolean success = orchestratorService.pauseReminder(chatId);

        if (success) {
            logger.info(" Successfully paused reminder for chat: {}", chatId);
        } else {
            logger.info(" No active instances to pause for chat: {}", chatId);
        }
    }

    /**
     * ОБРАБОТКА КОМАНДЫ ВОЗОБНОВЛЕНИЯ
     */
    private void handleResumeCommand(Long chatId) {
        logger.info(" Processing resume request for chat: {}", chatId);

        // ИСПОЛЬЗУЕМ НОВЫЙ ORCHESTRATOR ДЛЯ ВОЗОБНОВЛЕНИЯ
        boolean success = orchestratorService.resumeReminder(chatId);

        if (success) {
            logger.info(" Successfully resumed reminder for chat: {}", chatId);
        } else {
            logger.info(" No paused instances to resume for chat: {}", chatId);
        }
    }

    /**
     * ОБРАБОТКА СОЗДАНИЯ НАПОМИНАНИЯ (совместимость с обеими архитектурами)
     */
    private void handleNotificationCreation(String messageText, Long chatId,
                                            String userName, String username) {
        logger.info("🆕 Attempting to create notification for user: {} (Chat ID: {})",
                userName, chatId);

        // ИСПОЛЬЗУЕМ СТАРЫЙ СЕРВИС ДЛЯ ПАРСИНГА (обратная совместимость)
        ParseResult result = notificationTaskService.parseMessage(messageText);

        if (result.isSuccess()) {
            // УСПЕШНЫЙ ПАРСИНГ - СОЗДАЕМ НАПОМИНАНИЕ ЧЕРЕЗ НОВУЮ АРХИТЕКТУРУ
            var task = result.getTask();
            var instance = orchestratorService.createReminder(
                    chatId, username, userName,
                    task.getNotificationText(),
                    task.getNotificationDateTime()
            );

            // ОТПРАВЛЯЕМ ПОДТВЕРЖДЕНИЕ ПОЛЬЗОВАТЕЛЮ
            String successMessage = String.format(
                    " **Напоминание успешно создано!** \n\n" +
                            " **Текст:** %s\n" +
                            " **Время:** %s\n" +
                            " **ID:** %d\n\n" +
                            "Я буду присылать вам это напоминание 6 раз с интервалом 10 минут, " +
                            "начиная с указанного времени.\n\n" +
                            " **Команды управления:**\n" +
                            " `Ок` - подтвердить выполнение\n" +
                            " `Пауза` - приостановить на 1 час\n" +
                            " `Возобновить` - возобновить отправки",
                    instance.getNotificationText(),
                    instance.getScheduledTime().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")),
                    instance.getId()
            );

            sendMessage(chatId, successMessage);
            logger.info(" Notification created successfully for user: {} (Chat ID: {}), Instance ID: {}",
                    userName, chatId, instance.getId());

        } else {
            // ОШИБКА ПАРСИНГА
            String errorMessage = result.getErrorMessage();
            String detailedErrorMessage = String.format(
                    " **Ошибка создания напоминания:** %s\n\n%s",
                    errorMessage, correctMessageFormat
            );

            sendMessage(chatId, detailedErrorMessage);
            logger.warn(" Failed to create notification for user: {} (Chat ID: {}). Error: {}",
                    userName, chatId, errorMessage);
        }
    }

    // ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ (без изменений)

    /**
     * ПРОВЕРКА ВАЛИДНОСТИ ВХОДЯЩЕГО СООБЩЕНИЯ
     */
    private boolean isValidMessage(Update update) {
        return update != null &&
                update.message() != null &&
                update.message().text() != null &&
                !update.message().text().trim().isEmpty();
    }

    /**
     * ПРОВЕРКА КОМАНДЫ ПОДТВЕРЖДЕНИЯ
     */
    private boolean isConfirmationCommand(String messageText) {
        if (messageText == null) return false;

        String trimmedText = messageText.trim().toLowerCase();

        return trimmedText.equals("ок") ||
                trimmedText.equals("ok") ||
                trimmedText.equals("готово") ||
                trimmedText.equals("сделал") ||
                trimmedText.equals("выполнил") ||
                trimmedText.equals("done") ||
                trimmedText.equals("сделано") ||
                trimmedText.equals("выполнено") ||
                trimmedText.equals("готов");
    }

    /**
     * ПРОВЕРКА КОМАНДЫ ПАУЗЫ
     */
    private boolean isPauseCommand(String messageText) {
        if (messageText == null) return false;

        String trimmedText = messageText.trim().toLowerCase();

        return trimmedText.equals("пауза") ||
                trimmedText.equals("pause") ||
                trimmedText.equals("приостановить") ||
                trimmedText.equals("остановить") ||
                trimmedText.equals("стоп");
    }

    /**
     * ПРОВЕРКА КОМАНДЫ ВОЗОБНОВЛЕНИЯ
     */
    private boolean isResumeCommand(String messageText) {
        if (messageText == null) return false;

        String trimmedText = messageText.trim().toLowerCase();

        return trimmedText.equals("возобновить") ||
                trimmedText.equals("resume") ||
                trimmedText.equals("продолжить") ||
                trimmedText.equals("continue") ||
                trimmedText.equals("старт");
    }

    /**
     * ОТПРАВКА СООБЩЕНИЯ ОБ ОШИБКЕ
     */
    private void sendErrorMessage(Long chatId, String error) {
        sendMessage(chatId, "!X!" + error);
    }

    /**
     * ОТПРАВКА СООБЩЕНИЯ В TELEGRAM
     */
    private void sendMessage(Long chatId, String message) {
        try {
            SendMessage sendMessage = new SendMessage(chatId, message);
            var response = telegramBot.execute(sendMessage);

            if (response.isOk()) {
                logger.debug(" Successfully sent message to chat: {}, length: {} chars",
                        chatId, message.length());
            } else {
                logger.error(" Failed to send message to chat: {}, error: {}",
                        chatId, response.description());
            }

        } catch (Exception e) {
            logger.error(" Exception while sending message to chat: {}", chatId, e);
        }
    }
}