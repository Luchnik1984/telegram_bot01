package pro.sky.telegrambot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import pro.sky.telegrambot.model.Chat;
import pro.sky.telegrambot.model.NotificationTemplate;

import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {
    /**
     * Найти все шаблоны для чата
     */
    List<NotificationTemplate> findByChatOrderByCreatedAtDesc(Chat chat);

    /**
     * Найти активные шаблоны для чата
     */
    List<NotificationTemplate> findByChatAndIsActiveTrueOrderByUsageCountDesc(Chat chat);

    /**
     * Найти шаблон по тексту и чату
     */
    Optional<NotificationTemplate> findByChatAndTemplateText(Chat chat, String templateText);

    /**
     * Увеличить счетчик использования шаблона
     */
    @Modifying
    @Query("UPDATE NotificationTemplate t SET t.usageCount = t.usageCount + 1 WHERE t.id = :templateId")
    void incrementUsageCount(@Param("templateId") Long templateId);

    /**
     * Деактивировать все шаблоны для чата
     */
    @Modifying
    @Query("UPDATE NotificationTemplate t SET t.isActive = false WHERE t.chat = :chat")
    void deactivateAllByChat(@Param("chat") Chat chat);

}
