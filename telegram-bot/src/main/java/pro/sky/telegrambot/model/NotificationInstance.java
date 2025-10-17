package pro.sky.telegrambot.model;

import pro.sky.telegrambot.model.enums.NotificationStatus;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table (name = "notification_instance")
public class NotificationInstance {
    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne (fetch = FetchType.LAZY)
    @JoinColumn (name = "chat_id", nullable = false)
    private Chat chat;

    @ManyToOne (fetch = FetchType.LAZY)
    @JoinColumn (name = "template_id")
    private NotificationTemplate template;

    @Column (name ="notification_text", nullable = false, columnDefinition = "TEXT")
    private String notificationText;

    @Column (name = "scheduled_time", nullable = false)
    private LocalDateTime scheduledTime;

    @Column (name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt=LocalDateTime.now();

    @Enumerated (EnumType.STRING)
    @Column (name = "status", nullable = false)
    private NotificationStatus status = NotificationStatus.PENDING;

    @OneToOne(mappedBy = "instance", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private NotificationSendingState sendingState;

    public NotificationInstance() {
    }

    public NotificationInstance(Chat chat, String notificationText, LocalDateTime scheduledTime) {
        this.chat = chat;
        this.notificationText = notificationText;
        this.scheduledTime = scheduledTime;
    }

    public NotificationInstance(Chat chat, NotificationTemplate template, String notificationText, LocalDateTime scheduledTime) {
        this.chat = chat;
        this.template = template;
        this.notificationText = notificationText;
        this.scheduledTime = scheduledTime;
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

    public NotificationTemplate getTemplate() {
        return template;
    }

    public void setTemplate(NotificationTemplate template) {
        this.template = template;
    }

    public String getNotificationText() {
        return notificationText;
    }

    public void setNotificationText(String notificationText) {
        this.notificationText = notificationText;
    }

    public LocalDateTime getScheduledTime() {
        return scheduledTime;
    }

    public void setScheduledTime(LocalDateTime scheduledTime) {
        this.scheduledTime = scheduledTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public NotificationStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationStatus status) {
        this.status = status;
    }

    public NotificationSendingState getSendingState() {
        return sendingState;
    }

    public void setSendingState(NotificationSendingState sendingState) {
        this.sendingState = sendingState;
    }
}
