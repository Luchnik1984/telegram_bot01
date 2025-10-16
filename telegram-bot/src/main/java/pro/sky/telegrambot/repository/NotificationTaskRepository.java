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
     * Кастомный запрос для поиска напоминаний с учетом времени предыдущего запуска.
     * Ищет напоминания между временем предыдущего запуска и текущим временем
     *
     * @param dateTime текущее время
     * @param previousRunTime время предыдущего запуска шедулера
     * @return список напоминаний для отправки
     */
    @Query("SELECT n FROM NotificationTask n WHERE n.notificationDateTime <= :dateTime AND n.notificationDateTime > :previousRunTime")
    List<NotificationTask> findByNotificationToSend(@Param("dateTime") LocalDateTime dateTime,
                                                    @Param("previousRunTime") LocalDateTime previousRunTime);

    /**
     * Находит напоминания для точной минуты.
     * Используется шедулером для поиска напоминаний которые должны быть отправлены в текущую минуту
     *
     * @param dateTime время округленное до минут (например: 2025-10-15T12:30:00)
     * @return список напоминаний запланированных на указанную минуту
     */
    List<NotificationTask> findByNotificationDateTime(LocalDateTime dateTime);

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
     * Находит напоминания старше указанной даты (по времени напоминания)
     * Используется для поиска просроченных напоминаний
     *
     * @param dateTime пороговая дата
     * @return список старых напоминаний
     */
    List<NotificationTask> findByNotificationDateTimeBefore(LocalDateTime dateTime);

    /**
     * Находит напоминания в указанном временном диапазоне (по времени напоминания).
     * Используется для поиска напоминаний в определенном периоде
     *
     * @param start начало диапазона (включительно)
     * @param end конец диапазона (включительно)
     * @return список напоминаний в указанном диапазоне
     */
    List<NotificationTask> findByNotificationDateTimeBetween(LocalDateTime start, LocalDateTime end);

    /**
     * Находит напоминания по идентификатору чата и тексту напоминания.
     * Может использоваться для проверки дубликатов
     *
     * @param chatId идентификатор чата
     * @param notificationText текст напоминания
     * @return список найденных напоминаний
     */
    List<NotificationTask> findByChatIdAndNotificationText(Long chatId, String notificationText);

    /**
     * Удаляет все напоминания для конкретного пользователя.
     * Может использоваться для очистки всех напоминаний пользователя
     *
     * @param chatId идентификатор чата
     * @return количество удаленных записей
     */
    long deleteByChatId(Long chatId);

    /**
     * Удаляет напоминания старше указанной даты (по времени создания)
     * Используется для массовой очистки старых данных
     *
     * @param dateTime пороговая дата
     * @return количество удаленных записей
     */
    long deleteByCreatedAtBefore(LocalDateTime dateTime);

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
}