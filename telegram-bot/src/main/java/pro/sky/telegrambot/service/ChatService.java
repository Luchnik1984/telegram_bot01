package pro.sky.telegrambot.service;

import pro.sky.telegrambot.model.Chat;
import pro.sky.telegrambot.repository.ChatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
public class ChatService {

    private static final Logger logger = LoggerFactory.getLogger(ChatService.class);

    private final ChatRepository chatRepository;

    public ChatService(ChatRepository chatRepository) {
        this.chatRepository = chatRepository;
    }

    /**
     * Найти или создать чат по telegram chatId
     */
    public Chat findOrCreateChat(Long chatId, String username, String firstName) {
        logger.info("Finding or creating chat: chatId={}, username={}, firstName={}",
                chatId, username, firstName);

        // ИСПРАВЛЕНИЕ: используем правильный метод репозитория
        Optional<Chat> existingChat = chatRepository.findByChatId(chatId);

        if (existingChat.isPresent()) {
            Chat chat = existingChat.get();
            chat.setLastActivity(LocalDateTime.now());

            // Обновляем username если изменился
            if (username != null && !username.equals(chat.getUsername())) {
                chat.setUsername(username);
            }
            if (firstName != null && !firstName.equals(chat.getFirstName())) {
                chat.setFirstName(firstName);
            }

            Chat savedChat = chatRepository.save(chat);
            logger.info("Found existing chat: {}", savedChat.getId());
            return savedChat;
        } else {
            Chat newChat = new Chat(chatId, username, firstName);
            Chat savedChat = chatRepository.save(newChat);
            logger.info("Created new chat: {}", savedChat.getId());
            return savedChat;
        }
    }

    /**
     * Найти чат по telegram chatId
     */
    public Optional<Chat> findByChatId(Long chatId) {
        return chatRepository.findByChatId(chatId);
    }

    /**
     * Обновить время последней активности
     */
    public void updateLastActivity(Long chatId) {
        chatRepository.findByChatId(chatId).ifPresent(chat -> {
            chat.setLastActivity(LocalDateTime.now());
            chatRepository.save(chat);
            logger.debug("Updated last activity for chat: {}", chatId);
        });
    }

    /**
     * Проверить существование чата
     */
    public boolean existsByChatId(Long chatId) {
        return chatRepository.existsByChatId(chatId);
    }

    /**
     * Получить все чаты с активными напоминаниями
     */
    public java.util.List<Chat> getChatsWithActiveNotifications() {
        return chatRepository.findChatsWithActiveNotifications();
    }

    /**
     * Создать новый чат (если точно известно, что его нет)
     */
    public Chat createChat(Long chatId, String username, String firstName) {
        logger.info("Creating new chat: chatId={}, username={}, firstName={}",
                chatId, username, firstName);

        Chat newChat = new Chat(chatId, username, firstName);
        Chat savedChat = chatRepository.save(newChat);

        logger.info("Created new chat with ID: {}", savedChat.getId());
        return savedChat;
    }

    /**
     * Удалить чат (для администрирования)
     */
    public void deleteChat(Long chatId) {
        chatRepository.findByChatId(chatId).ifPresent(chat -> {
            chatRepository.delete(chat);
            logger.info("Deleted chat: {}", chatId);
        });
    }

    /**
     * Получить статистику по чатам
     */
    public String getChatsStatistics() {
        long totalChats = chatRepository.count();
        long activeChats = chatRepository.findChatsWithActiveNotifications().size();

        return String.format(
                " Статистика чатов:\n" +
                        "• Всего чатов: %d\n" +
                        "• Активных (с напоминаниями): %d\n" +
                        "• Неактивных: %d",
                totalChats, activeChats, totalChats - activeChats
        );
    }
}
