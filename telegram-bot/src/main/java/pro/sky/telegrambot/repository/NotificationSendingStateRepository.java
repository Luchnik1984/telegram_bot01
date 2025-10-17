package pro.sky.telegrambot.repository;

import pro.sky.telegrambot.model.NotificationInstance;
import pro.sky.telegrambot.model.NotificationSendingState;
import pro.sky.telegrambot.model.enums.SendingPhase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationSendingStateRepository extends JpaRepository<NotificationSendingState, Long> {

    /**
     * Найти состояние по экземпляру
     */
    Optional<NotificationSendingState> findByInstance(NotificationInstance instance);

    /**
     * Найти состояние по ID экземпляра
     */
    @Query("SELECT ss FROM NotificationSendingState ss WHERE ss.instance.id = :instanceId")
    Optional<NotificationSendingState> findByInstanceId(@Param("instanceId") Long instanceId);

    /**
     * Найти состояния готовые к отправке
     */
    @Query("SELECT ss FROM NotificationSendingState ss " +
            "WHERE ss.sendingPhase IN ('INITIAL', 'REPEAT') " +
            "AND ss.sendCount < ss.maxAttempts " +
            "AND (ss.pauseUntil IS NULL OR ss.pauseUntil <= :currentTime) " +
            "AND (ss.nextScheduledSend IS NULL OR ss.nextScheduledSend <= :currentTime)")
    List<NotificationSendingState> findReadyToSendStates(@Param("currentTime") LocalDateTime currentTime);
    /**
     * Найти состояния требующие обновления следующей отправки
     */
    @Query("SELECT ss FROM NotificationSendingState ss " +
            "WHERE ss.sendingPhase = 'REPEAT' " +
            "AND ss.sendCount < ss.maxAttempts " +
            "AND ss.nextScheduledSend IS NULL")
    List<NotificationSendingState> findStatesNeedingNextSchedule();

    /**
     * Обновить фазу отправки
     */
    @Modifying
    @Query("UPDATE NotificationSendingState ss SET ss.sendingPhase = :phase WHERE ss.id = :id")
    void updateSendingPhase(@Param("id") Long id, @Param("phase") SendingPhase phase);

    /**
     * Поставить отправки на паузу
     */
    @Modifying
    @Query("UPDATE NotificationSendingState ss SET ss.sendingPhase = 'PAUSED', " +
            "ss.pauseUntil = :pauseUntil WHERE ss.instance.id = :instanceId")
    void pauseSending(@Param("instanceId") Long instanceId,
                      @Param("pauseUntil") LocalDateTime pauseUntil);

    /**
     * Возобновить отправки
     */
    @Modifying
    @Query("UPDATE NotificationSendingState ss SET ss.sendingPhase = 'REPEAT', " +
            "ss.pauseUntil = NULL WHERE ss.instance.id = :instanceId")
    void resumeSending(@Param("instanceId") Long instanceId);

    /**
     * Найти состояния с истекшей паузой
     */
    @Query("SELECT ss FROM NotificationSendingState ss " +
            "WHERE ss.sendingPhase = 'PAUSED' " +
            "AND ss.pauseUntil <= :currentTime")
    List<NotificationSendingState> findExpiredPauses(@Param("currentTime") LocalDateTime currentTime);
}
