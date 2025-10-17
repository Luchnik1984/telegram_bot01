package pro.sky.telegrambot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pro.sky.telegrambot.model.NotificationDelivery;
import pro.sky.telegrambot.model.NotificationInstance;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    /**
     * Найти все отправки для экземпляра
     */
    List<NotificationDelivery> findByInstanceOrderByDeliveryTimeDesc(NotificationInstance instance);

    /**
     * Найти отправки за период
     */
    List<NotificationDelivery> findByDeliveryTimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Получить статистику доставки для экземпляра
     */
    @Query("SELECT COUNT(nd) FROM NotificationDelivery nd " +
            "WHERE nd.instance = :instance " +
            "AND nd.success = true")
    Long countSuccessfulDeliveries(@Param("instance") NotificationInstance instance);

    /**
     * Получить среднее время доставки для чата
     */
    @Query("SELECT AVG(nd.deliveryDurationMs) FROM NotificationDelivery nd " +
            "JOIN nd.instance ni " +
            "JOIN ni.chat c " +
            "WHERE c.chatId = :chatId " +
            "AND nd.deliveryDurationMs IS NOT NULL")
    Double getAverageDeliveryTimeByChatId(@Param("chatId") Long chatId);

    /**
     * Получить последнюю отправку для экземпляра
     */
    @Query("SELECT nd FROM NotificationDelivery nd " +
            "WHERE nd.instance = :instance " +
            "ORDER BY nd.deliveryTime DESC " +
            "LIMIT 1")
    Optional<NotificationDelivery> findLastDeliveryForInstance(@Param("instance") NotificationInstance instance);
}
