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

    private Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);

    @Autowired
    private TelegramBot telegramBot;
    @Autowired
    private NotificationTaskService notificationTaskService;

    @Autowired
    private NotificationTaskRepository notificationTaskRepository;
    @Autowired
    private NotificationSchedulerService notificationSchedulerService;

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
    }

    @Override
    public int process(List<Update> updates) {
        updates.forEach(update -> {
            logger.info("Processing update: {}", update);

            // Проверяем, что сообщение не пустое и содержит текст
            if (update.message()!=null&&update.message().text()!=null) {
                String messageText = update.message().text();
                Long chatId = update.message().chat().id();

                // Обрабатываем команду /start
                if("/start".equals(messageText)){
                    // Создаем и отправляем приветственное сообщение
                    sendMessage(chatId, "Привет! Я твой телеграмм бот. Рад приветствовать!\n\n" +
                            correctMassageForm);
                }
                // Обрабатываем подтверждение "Ок" для последнего напоминания
                else if (isConfirmationCommand(messageText)) {
                    handleLastNotificationConfirmation(chatId);
                }

                    // Пытаемся обработать как напоминание
                else {
                   ParseResult result = notificationTaskService.parseAndSaveNotification(messageText,
                           chatId);
                   if(result.isSuccess()){
                       sendMessage(chatId,"Напоминание успешно создано!");
                       logger.info("Notification saved successfully for chat: {}", chatId);
                   }else {
                       String errorMessage = result.getErrorMessage();
                       sendMessage(chatId,errorMessage +"\n\n"+
                               "Правильный формат: \n"+
                               correctMassageForm);
                       logger.warn("Failed to save notification for chat: {}, message: {}",
                               chatId,
                               messageText);
                   }
                }
            }
        });
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }

    /**
     * Обрабатывает подтверждение последнего отправленного напоминания
     * @param chatId идентификатор чата
     */
    private void handleLastNotificationConfirmation(Long chatId) {
        logger.info("Handling confirmation for chat: {}", chatId);
        try {
            // Получаем ID последнего отправленного напоминания через инжектированный сервис
            Long lastNotificationId = notificationSchedulerService.getLastSentNotificationId(chatId);

            if (lastNotificationId == null) {
                sendMessage(chatId, "Нет активных напоминаний для подтверждения 📭");
                return;
            }

            // Ищем напоминание по ID
            Optional<NotificationTask> notificationOptional =
                    notificationTaskRepository.findById(lastNotificationId);

            if(notificationOptional.isEmpty()){
                sendMessage(chatId, "Напоминание не найдено");

                // Очищаем устаревший ID из кэша
                notificationSchedulerService.clearLastSentNotification(chatId);
                return;
            }

            NotificationTask notification = notificationOptional.get();

            // Дополнительная проверка, что напоминание принадлежит этому пользователю
            if (!notification.getChatId().equals(chatId)) {
                sendMessage(chatId, "Это не ваше напоминание!");
                // Очищаем некорректный ID из кэша
                notificationSchedulerService.clearLastSentNotification(chatId);
                return;

            }
            // Удаляем напоминание из базы данных
            notificationTaskRepository.delete(notification);
            // Очищаем из кэша
            notificationSchedulerService.clearLastSentNotification(chatId);

            sendMessage(chatId, "Напоминание удалено! \n"+
                    "Текст: "+
                    notification.getNotificationText());
            logger.info("Deleted last sent notification ID: {} for chat: {}", lastNotificationId, chatId);

        } catch (Exception e) {
            logger.error("Error handling last notification confirmation for chat: {}", chatId, e);
            sendMessage(chatId,"Произошла ошибка при обработке подтверждения");
        }
    }

    /**
     * Проверяет, является ли сообщение командой подтверждения
     * @param messageText текст сообщения
     * @return true если это команда подтверждения
     */
    private boolean isConfirmationCommand(String messageText) {
        if (messageText == null) return false;

        String trimmedText = messageText.trim().toLowerCase();
        return trimmedText.equals("ок") ||
                trimmedText.equals("ok") ||
                trimmedText.equals("готово") ||
                trimmedText.equals("сделал") ||
                trimmedText.equals("выполнил") ||
                trimmedText.equals("done");
    }

    private void sendMessage(Long chatId, String message) {
        SendMessage sendMessage = new SendMessage(chatId, message);
        telegramBot.execute(sendMessage);
        logger.info(" Send message to chat: {}", chatId);
    }

    String correctMassageForm =
            "дд.мм.гггг чч:мм Текст напоминания\n\n"+
            "Например:\n"+
            "24.01.2025 10:00 Сдать домашнюю работу";
}
