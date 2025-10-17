package pro.sky.telegrambot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pro.sky.telegrambot.model.Chat;
import pro.sky.telegrambot.model.NotificationInstance;
import pro.sky.telegrambot.model.enums.NotificationStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationInstanceRepository extends JpaRepository<NotificationInstance, Long> {

    /**
     * Найти все экземпляры для чата
     */
    List<NotificationInstance> findByChatOrderByScheduledTimeDesc(Chat chat);

    /**
     * Найти экземпляры по статусу
     */
    List<NotificationInstance> findByChatAndStatusOrderByScheduledTimeDesc(
            Chat chat, NotificationStatus status);

    /**
     * Найти активные экземпляры для отправки (PENDING или ACTIVE)
     */
    @Query("SELECT ni FROM NotificationInstance ni " +
            "WHERE ni.status IN ('PENDING', 'ACTIVE') " +
            "AND ni.scheduledTime <= :currentTime " +
            "AND ni.createdAt >= :createdAfter " +
            "ORDER BY ni.scheduledTime ASC")
    List<NotificationInstance> findDueInstances(
            @Param("currentTime") LocalDateTime currentTime,
            @Param("createdAfter") LocalDateTime createdAfter);

    /**
     * Найти экземпляры для отправки в текущую минуту
     */
    @Query("SELECT ni FROM NotificationInstance ni " +
            "WHERE ni.status IN ('PENDING', 'ACTIVE') " +
            "AND DATE_TRUNC('minute', ni.scheduledTime) = DATE_TRUNC('minute', :currentTime) " +
            "AND ni.createdAt >= :oneHourAgo")
    List<NotificationInstance> findInstancesForCurrentMinute(
            @Param("currentTime") LocalDateTime currentTime,
            @Param("oneHourAgo") LocalDateTime oneHourAgo);

    /**
     * Найти последнее активное напоминание для чата
     */
    @Query("SELECT ni FROM NotificationInstance ni " +
            "WHERE ni.chat.chatId = :chatId " +
            "AND ni.status = 'ACTIVE' " +
            "ORDER BY ni.scheduledTime DESC " +
            "LIMIT 1")
    Optional<NotificationInstance> findLastActiveByChatId(@Param("chatId") Long chatId);

    /**
     * Обновить статус экземпляра
     */
    @Modifying
    @Query("UPDATE NotificationInstance ni SET ni.status = :status WHERE ni.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") NotificationStatus status);

    /**
     * Удалить старые завершенные экземпляры
     */
    @Modifying
    @Query("DELETE FROM NotificationInstance ni WHERE ni.createdAt < :cutoffTime " +
            "AND ni.status IN ('COMPLETED', 'EXPIRED', 'CANCELLED')")
    void deleteOldCompletedInstances(@Param("cutoffTime") LocalDateTime cutoffTime);

    /**
     * Подсчитать количество экземпляров по времени создания и статусам
     */
    @Query("SELECT COUNT(ni) FROM NotificationInstance ni WHERE ni.createdAt < :cutoffTime " +
            "AND ni.status IN :statuses")
    long countByCreatedAtBeforeAndStatusIn(@Param("cutoffTime") LocalDateTime cutoffTime,
                                           @Param("statuses") List<NotificationStatus> statuses);

    /**
     * Найти экземпляры созданные до указанного времени
     */
    List<NotificationInstance> findByCreatedAtBefore(LocalDateTime time);

    /**
     * Найти экземпляры по статусу
     */
    List<NotificationInstance> findByStatus(NotificationStatus status);

    /**
     * Найти экземпляры по чату и статусу
     */
    List<NotificationInstance> findByChatAndStatus(Chat chat, NotificationStatus status);
}
