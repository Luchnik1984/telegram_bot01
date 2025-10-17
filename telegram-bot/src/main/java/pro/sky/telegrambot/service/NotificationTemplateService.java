package pro.sky.telegrambot.service;

import pro.sky.telegrambot.model.Chat;
import pro.sky.telegrambot.model.NotificationTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pro.sky.telegrambot.repository.NotificationTemplateRepository;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class NotificationTemplateService {
    private static final Logger logger = LoggerFactory.getLogger(NotificationTemplateService.class);

    private final NotificationTemplateRepository templateRepository;

    public NotificationTemplateService(NotificationTemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    /**
     * Создать или найти существующий шаблон
     */
    public NotificationTemplate createOrFindTemplate(Chat chat, String templateText) {
        logger.info("Creating or finding template for chat: {}, text: {}",
                chat.getId(), templateText);

        Optional<NotificationTemplate> existingTemplate =
                templateRepository.findByChatAndTemplateText(chat, templateText);

        if (existingTemplate.isPresent()) {
            NotificationTemplate template = existingTemplate.get();
            templateRepository.incrementUsageCount(template.getId());
            template.setIsActive(true);
            logger.info("Found existing template: {}, usage count: {}",
                    template.getId(), template.getUsageCount() + 1);
            return templateRepository.save(template);
        } else {
            NotificationTemplate newTemplate = new NotificationTemplate(chat, templateText);
            NotificationTemplate savedTemplate = templateRepository.save(newTemplate);
            logger.info("Created new template: {}", savedTemplate.getId());
            return savedTemplate;
        }
    }

    /**
     * Получить все шаблоны для чата
     */
    public List<NotificationTemplate> getTemplatesByChat(Chat chat) {
        return templateRepository.findByChatOrderByCreatedAtDesc(chat);
    }

    /**
     * Получить активные шаблоны для чата
     */
    public List<NotificationTemplate> getActiveTemplatesByChat(Chat chat) {
        return templateRepository.findByChatAndIsActiveTrueOrderByUsageCountDesc(chat);
    }

    /**
     * Деактивировать шаблон
     */
    public void deactivateTemplate(Long templateId) {
        templateRepository.findById(templateId).ifPresent(template -> {
            template.setIsActive(false);
            templateRepository.save(template);
            logger.info("Deactivated template: {}", templateId);
        });
    }

    /**
     * Активировать шаблон
     */
    public void activateTemplate(Long templateId) {
        templateRepository.findById(templateId).ifPresent(template -> {
            template.setIsActive(true);
            templateRepository.save(template);
            logger.info("Activated template: {}", templateId);
        });
    }

    /**
     * Удалить шаблон
     */
    public void deleteTemplate(Long templateId) {
        templateRepository.deleteById(templateId);
        logger.info("Deleted template: {}", templateId);
    }

    /**
     * Увеличить счетчик использования шаблона
     */
    public void incrementUsageCount(Long templateId) {
        templateRepository.incrementUsageCount(templateId);
        logger.debug("Incremented usage count for template: {}", templateId);
    }
}
