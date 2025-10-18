package pro.sky.telegrambot.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table (name = "notification_task")
public class NotificationTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column (name = "chat_id", nullable = false)
    private Long chatId;

    @Column (name = "notification_text", nullable = false, columnDefinition = "TEXT")
    private String notificationText;

    @Column (name = "notification_date_time", nullable = false)
    private LocalDateTime notificationDateTime;

    @Column (name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public NotificationTask() {
        // Пустой конструктор для JPA
    }

    public NotificationTask(Long chatId, String notificationText, LocalDateTime notificationDateTime) {
        this.chatId = chatId;
        this.notificationText = notificationText;
        this.notificationDateTime = notificationDateTime;
        this.createdAt = LocalDateTime.now();
    }

    public long getId() {
        return id;
    }

    public Long getChatId() {
        return chatId;
    }


    public String getNotificationText() {
        return notificationText;
    }

    public void setNotificationText(String notificationText) {
        this.notificationText = notificationText;
    }

    public LocalDateTime getNotificationDateTime() {
        return notificationDateTime;
    }

    public void setNotificationDateTime(LocalDateTime notificationDateTime) {
        this.notificationDateTime = notificationDateTime;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }


    @Override
    public String toString() {
        return "NotificationTask{" +
                "id=" + id +
                ", chatId=" + chatId +
                ", notificationText='" + notificationText + '\'' +
                ", notificationDateTime=" + notificationDateTime +
                ", createdAt=" + createdAt +
                '}';
    }
}
