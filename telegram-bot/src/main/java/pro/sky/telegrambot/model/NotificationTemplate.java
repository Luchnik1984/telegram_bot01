package pro.sky.telegrambot.model;

import javax.persistence.*;

@Entity
@Table(name = "notification_template")
public class NotificationTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_id",nullable = false)
    private Chat chat;

    @Column(name = "template_text", nullable = false, columnDefinition = "TEXT")
    private String templateText;

    @Column (name = "is_active",nullable = false)
    private Boolean isActive=true;

    @Column (name = "usage_count")
    private Integer usageCount = 0;

    public NotificationTemplate() {
    }

    public NotificationTemplate(Chat chat, String templateText) {
        this.chat = chat;
        this.templateText = templateText;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Chat getChat() {
        return chat;
    }

    public void setChat(Chat chat) {
        this.chat = chat;
    }

    public String getTemplateText() {
        return templateText;
    }

    public void setTemplateText(String templateText) {
        this.templateText = templateText;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }

    public Integer getUsageCount() {
        return usageCount;
    }

    public void setUsageCount(Integer usageCount) {
        this.usageCount = usageCount;
    }

    public void incrementUsageCount() {
        this.usageCount++;
    }

    @Override
    public String toString() {
        return "NotificationTemplate{" +
                "id=" + id +
                ", chat=" + (chat !=null ? chat.getId() : "null") +
                ", templateText='" + templateText + '\'' +
                ", isActive=" + isActive +
                ", usageCount=" + usageCount +
                '}';
    }
}
