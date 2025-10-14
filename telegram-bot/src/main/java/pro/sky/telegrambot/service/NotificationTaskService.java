package pro.sky.telegrambot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.model.NotificationTask;
import pro.sky.telegrambot.model.ParseResult;
import pro.sky.telegrambot.repository.NotificationTaskRepository;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class NotificationTaskService {
    private final NotificationTaskRepository repository;
    private static final Logger logger = LoggerFactory.getLogger(NotificationTaskService.class);

    // Паттерн для распознавания даты и текста напоминания
    private static final Pattern PATTERN = Pattern.compile("(\\d{2}\\.\\d{2}\\.\\d{4}\\s\\d{2}:\\d{2})\\s*(.*)");

    // Паттерн для преобразования строки в LocalDateTime
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public NotificationTaskService(NotificationTaskRepository repository) {
        this.repository = repository;
    }

    /**
     * Пытается распарсить сообщение и создать напоминание
     * @param messageText текст сообщения от пользователя
     * @param chatId идентификатор чата
     * @return true если напоминание успешно создано, false если формат неверный
     */

    public ParseResult parseAndSaveNotification(String messageText, long chatId) {
        logger.info("Attempting to parse message: '{}' for chat: {}", messageText, chatId);
        Matcher matcher = PATTERN.matcher(messageText);

        if (!matcher.matches()) {
            logger.warn("Message does not match pattern: '{}'", messageText);
            return ParseResult.error("Неверный формат сообщения!");
        }


            logger.info("Pattern matched! Extracting date and text...");
            try {
                // Извлекаем дату/время и текст напоминания
                String dateTimeString = matcher.group(1);
                String notificationText = matcher.group(2);

                logger.info("BEFORE trim - DateTime: '{}', Text: '{}'", dateTimeString, notificationText);
                logger.info("Text length before trim: {}", notificationText.length());

                notificationText = notificationText.trim();

                logger.info("AFTER trim - DateTime: '{}', Text: '{}'", dateTimeString, notificationText);
                logger.info("Text length after trim: {}", notificationText.length());
                logger.info("Text isEmpty after trim: {}", notificationText.isEmpty());

                // Проверяем, что текст не пустой после trim();
                if (notificationText.isEmpty()){
                    logger.warn("Notification text is empty after trimming");
                    return ParseResult.error("Текст напоминания не может быть пустым!");
                }

                // Преобразуем строку в LocalDateTime
                LocalDateTime notificationDateTime = LocalDateTime.parse(dateTimeString, FORMATTER);
                logger.info("Parsed LocalDateTime: {}", notificationDateTime);

                // Проверяем, что дата не в прошлом
                LocalDateTime now = LocalDateTime.now();
                logger.info("Current time: {}", now);
                if (notificationDateTime.isBefore(LocalDateTime.now())){
                    logger.warn("Date is in the past: {} < {}", notificationDateTime, now);
                    return ParseResult.error("Нельзя создать напоминание в прошлом!");
                }

                // Создаем и сохраняем сущность
                NotificationTask task = new NotificationTask(chatId,
                        notificationText,
                        notificationDateTime);
                logger.info("Creating NotificationTask: {}", task);

                NotificationTask savedTask = repository.save(task);
                logger.info("Successfully saved NotificationTask with ID: {}", savedTask.getId());
                return ParseResult.success(savedTask);

            } catch (DateTimeParseException e){
                logger.error("Failed to parse date/time: {}", e.getMessage());
                // Если дата некорректная
                return ParseResult.error("Неверный формат даты или времени!");
            }
        }

}
