package pro.sky.telegrambot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pro.sky.telegrambot.model.Chat;

import java.util.Optional;

@Repository
public interface ChatRepository extends JpaRepository<Chat, Long> {
    /**
     * Найти чат по telegram chatId
     */
    Optional<Chat> findByChatId(Long chatId);

    /**
     * Проверить существование чата по telegram chatId
     */
    boolean existsByChatId(Long chatId);

    /**
     * Найти чаты с активными напоминаниями
     */
    @Query("SELECT DISTINCT c FROM Chat c " +
            "JOIN c.instances i " +
            "WHERE i.status IN (pro.sky.telegrambot.model.enums.NotificationStatus.PENDING, " +
            "pro.sky.telegrambot.model.enums.NotificationStatus.ACTIVE)")
    java.util.List<Chat> findChatsWithActiveNotifications();

    /**
     * Найти чаты без активности за последние N дней
     */
    @Query("SELECT c FROM Chat c WHERE c.lastActivity < :cutoffDate")
    java.util.List<Chat> findInactiveChats(@Param("cutoffDate") java.time.LocalDateTime cutoffDate);

    /**
     * Найти чаты по username
     */
    java.util.List<Chat> findByUsername(String username);

    /**
     * Найти чаты по firstName
     */
    java.util.List<Chat> findByFirstName(String firstName);
}


