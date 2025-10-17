package pro.sky.telegrambot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pro.sky.telegrambot.model.NotificationTask;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с напоминаниями в базе данных.
 * Наследует JpaRepository для базовых CRUD операций
 */
public interface NotificationTaskRepository extends JpaRepository<NotificationTask, Long> {

    /**
     * Найти напоминания по chatId пользователя
     * Используется для отображения списка напоминаний пользователя
     */
    List<NotificationTask> findByChatId(Long chatId);

    /**
     * Найти напоминания по точному времени уведомления.
     * Используется в старом шедулере для поиска напоминаний к отправке
     */
    List<NotificationTask> findByNotificationDateTime(LocalDateTime dateTime);

    /**
     * Найти напоминания созданные после указанного времени.
     * Используется для поиска активных напоминаний (созданных менее 1 часа назад)
     */
    List<NotificationTask> findByCreatedAtAfter(LocalDateTime dateTime);

    /**
     * Найти напоминания созданные до указанного времени.
     * Используется для очистки старых напоминаний
     */
    List<NotificationTask> findByCreatedAtBefore(LocalDateTime dateTime);


    /**
     * НАЙТИ НАПОМИНАНИЯ ДЛЯ ОТПРАВКИ В ТЕКУЩЕЕ ВРЕМЯ.
     * Ищет напоминания которые:
     * - Имеют статус PENDING или ACTIVE (ожидают отправки или активно отправляются)
     * - Время напоминания наступило или прошло
     * - Созданы не более 1 часа назад (активные напоминания)
     *
     * @param currentTime текущее время сервера
     * @param createdAfter время, после которого созданы напоминания (текущее время - 1 час)
     * @return список напоминаний готовых к отправке
     */
    @Query("SELECT nt FROM NotificationTask nt WHERE nt.status IN ('PENDING', 'ACTIVE') " +
            "AND nt.notificationDateTime <= :currentTime " +
            "AND nt.createdAt >= :createdAfter " +
            "ORDER BY nt.notificationDateTime ASC")
    List<NotificationTask> findDueNotifications(@Param("currentTime") LocalDateTime currentTime,
                                                @Param("createdAfter") LocalDateTime createdAfter);

    /**
     * НАЙТИ НАПОМИНАНИЯ ДЛЯ ТЕКУЩЕЙ МИНУТЫ.
     * Более точный поиск для шедулера - находит напоминания которые
     * должны быть отправлены именно в эту минуту (с точностью до минуты)
     * @param currentTime текущее время (округляется до минут)
     * @param oneHourAgo время 1 час назад для фильтрации старых напоминаний
     * @return список напоминаний для отправки в текущую минуту
     */
    @Query("SELECT nt FROM NotificationTask nt WHERE nt.status IN ('PENDING', 'ACTIVE') " +
            "AND FUNCTION('DATE_TRUNC', 'minute', nt.notificationDateTime) = FUNCTION('DATE_TRUNC', 'minute', :currentTime) " +
            "AND nt.createdAt >= :oneHourAgo " +
            "ORDER BY nt.notificationDateTime ASC")
    List<NotificationTask> findNotificationsForCurrentMinute(@Param("currentTime") LocalDateTime currentTime,
                                                             @Param("oneHourAgo") LocalDateTime oneHourAgo);

    /**
     * НАЙТИ ПОСЛЕДНЕЕ АКТИВНОЕ НАПОМИНАНИЕ ДЛЯ ЧАТА.
     * Используется при обработке команды "Ок" - чтобы знать какое напоминание
     * подтверждает пользователь
     * @param chatId идентификатор чата пользователя
     * @return Optional с последним активным напоминанием или empty если не найдено
     */
    @Query("SELECT nt FROM NotificationTask nt WHERE nt.chatId = :chatId " +
            "AND nt.status = 'ACTIVE' " +
            "ORDER BY nt.notificationDateTime DESC " +
            "LIMIT 1")
    Optional<NotificationTask> findLastActiveByChatId(@Param("chatId") Long chatId);

    /**
     * ОБНОВИТЬ СТАТУС НАПОМИНАНИЯ.
     * Выполняет массовое обновление статуса без загрузки entity в память
     * Эффективно для массовых операций
     * @param id ID напоминания
     * @param status новый статус (PENDING, ACTIVE, COMPLETED, EXPIRED, CANCELLED)
     */
    @Modifying
    @Query("UPDATE NotificationTask nt SET nt.status = :status WHERE nt.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    /**
     * УВЕЛИЧИТЬ СЧЕТЧИК ОТПРАВОК.
     * Атомарно увеличивает счетчик отправок и обновляет время последней отправки.
     * Используется при каждой успешной отправке напоминания
     *
     * @param id ID напоминания
     * @param lastSentTime время последней отправки
     */
    @Modifying
    @Query("UPDATE NotificationTask nt SET nt.sendCount = nt.sendCount + 1, " +
            "nt.lastSentTime = :lastSentTime WHERE nt.id = :id")
    void incrementSendCount(@Param("id") Long id, @Param("lastSentTime") LocalDateTime lastSentTime);

    /**
     * УСТАНОВИТЬ ВРЕМЯ ПЕРВОЙ ОТПРАВКИ.
     * Вызывается при первой отправке напоминания
     * @param id ID напоминания
     * @param firstSentTime время первой отправки
     */
    @Modifying
    @Query("UPDATE NotificationTask nt SET nt.firstSentTime = :firstSentTime, " +
            "nt.status = 'ACTIVE' WHERE nt.id = :id AND nt.firstSentTime IS NULL")
    void setFirstSentTime(@Param("id") Long id, @Param("firstSentTime") LocalDateTime firstSentTime);

    /**
     * НАЙТИ ИСТЕКШИЕ НАПОМИНАНИЯ.
     * Находит напоминания, которые были созданы более 1 часа назад
     * и еще не помечены как EXPIRED
     * @param expiryTime время истечения (текущее время - 1 час)
     * @return список истекших напоминаний
     */
    @Query("SELECT nt FROM NotificationTask nt WHERE nt.createdAt < :expiryTime " +
            "AND nt.status IN ('PENDING', 'ACTIVE')")
    List<NotificationTask> findExpiredNotifications(@Param("expiryTime") LocalDateTime expiryTime);

    /**
     * ПОМЕТИТЬ НАПОМИНАНИЯ КАК ИСТЕКШИЕ
     * Массово обновляет статус напоминаний на EXPIRED
     * для тех, которые созданы более 1 часа назад
     * @param expiryTime время истечения
     * @return количество обновленных записей
     */
    @Modifying
    @Query("UPDATE NotificationTask nt SET nt.status = 'EXPIRED' " +
            "WHERE nt.createdAt < :expiryTime AND nt.status IN ('PENDING', 'ACTIVE')")
    int markExpiredNotifications(@Param("expiryTime") LocalDateTime expiryTime);

    /**
     * УДАЛИТЬ СТАРЫЕ ВЫПОЛНЕННЫЕ НАПОМИНАНИЯ.
     * Очищает базу данных от старых выполненных напоминаний
     * для предотвращения бесконечного роста базы
     * @param cutoffTime время отсечения (например, 7 дней назад)
     * @return количество удаленных записей
     */
    @Modifying
    @Query("DELETE FROM NotificationTask nt WHERE nt.createdAt < :cutoffTime " +
            "AND nt.status IN ('COMPLETED', 'EXPIRED', 'CANCELLED')")
    int deleteOldCompletedTasks(@Param("cutoffTime") LocalDateTime cutoffTime);
}