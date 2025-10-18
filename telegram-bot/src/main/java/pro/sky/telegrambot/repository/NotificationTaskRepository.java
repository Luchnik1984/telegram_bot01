package pro.sky.telegrambot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import pro.sky.telegrambot.model.NotificationTask;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Репозиторий для работы с напоминаниями в базе данных.
 * Наследует JpaRepository для базовых CRUD операций
 */
public interface NotificationTaskRepository extends JpaRepository<NotificationTask, Long> {

    /**
     * Находит напоминания созданные после указанной даты.
     * Используется для поиска активных напоминаний (созданных менее 1 часа назад)
     *
     * @param dateTime пороговое время
     * @return список напоминаний созданных после указанного времени
     */
    List<NotificationTask> findByCreatedAtAfter(LocalDateTime dateTime);

    /**
     * Находит напоминания созданные до указанной даты.
     * Используется для очистки старых напоминаний (старше 1 часа)
     *
     * @param dateTime пороговое время
     * @return список напоминаний созданных до указанного времени
     */
    List<NotificationTask> findByCreatedAtBefore(LocalDateTime dateTime);

    /**
     * Кастомный запрос для поиска напоминаний которые нужно отправить.
     * Ищет напоминания которые должны быть отправлены до текущего времени
     *
     * @param currentTime текущее время
     * @return список напоминаний для отправки
     */
    @Query("SELECT n FROM NotificationTask n WHERE n.notificationDateTime <= :currentTime")
    List<NotificationTask> findNotificationsToSend(@Param("currentTime") LocalDateTime currentTime);

    /**
     * Находит все напоминания для конкретного пользователя.
     * Может использоваться для отображения списка напоминаний пользователя
     *
     * @param chatId идентификатор чата пользователя
     * @return список напоминаний пользователя
     */
    List<NotificationTask> findByChatId(Long chatId);

    /**
     * Находит все напоминания для конкретного пользователя, отсортированные по дате.
     * Удобно для отображения пользователю в хронологическом порядке
     *
     * @param chatId идентификатор чата пользователя
     * @return список напоминаний пользователя, отсортированный по дате отправки
     */
    List<NotificationTask> findByChatIdOrderByNotificationDateTime(Long chatId);

    /**
     * Проверяет существование напоминаний для конкретного пользователя.
     * Может использоваться для отображения статуса (есть/нет напоминания)
     *
     * @param chatId идентификатор чата
     * @return true если у пользователя есть напоминания
     */
    boolean existsByChatId(Long chatId);

    /**
     * Подсчитывает количество напоминаний для конкретного пользователя.
     * Может использоваться для статистики или ограничений
     *
     * @param chatId идентификатор чата
     * @return количество напоминаний пользователя
     */
    long countByChatId(Long chatId);

    /**
     * Удаляет все напоминания для конкретного пользователя.
     * Может использоваться для очистки всех напоминаний пользователя
     *
     * @param chatId идентификатор чата
     * @return количество удаленных записей
     */
    long deleteByChatId(Long chatId);
}