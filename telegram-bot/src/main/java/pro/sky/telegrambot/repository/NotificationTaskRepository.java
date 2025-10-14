package pro.sky.telegrambot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pro.sky.telegrambot.model.NotificationTask;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationTaskRepository extends JpaRepository<NotificationTask, Long> {
    /**
     * Находит все напоминания, которые нужно отправить в указанное время
     * @param dateTime текущее время для поиска подходящих напоминаний
     * @return список напоминаний для отправки
     */
    @Query ("SELECT n FROM NotificationTask n WHERE n.notificationDateTime <= :dateTime")
    List<NotificationTask> findByNotificationToSend(@Param("dateTime") LocalDateTime dateTime);

    /**
     * Находит все напоминания для конкретного пользователя
     * @param chatId идентификатор чата пользователя
     * @return список напоминаний пользователя
     */
    List<NotificationTask> findByChatId(Long chatId);

    /**
     * Находит все напоминания для конкретного пользователя, отсортированные по дате
     * @param chatId идентификатор чата пользователя
     * @return список напоминаний пользователя, отсортированный по дате отправки
     */
    List<NotificationTask> findByChatIdOrderByNotificationDateTime(Long chatId);

    /**
     * Находит напоминания старше указанной даты
     * @param dateTime пороговая дата
     * @return список старых напоминаний
     */
    List<NotificationTask> findByNotificationDateTimeBefore(LocalDateTime dateTime);

}
