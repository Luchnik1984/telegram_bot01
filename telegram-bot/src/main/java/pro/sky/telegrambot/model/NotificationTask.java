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

    @Column(name = "first_sent_time")
    private LocalDateTime firstSentTime;

    @Column(name = "send_count")
    private Integer sendCount = 0;

    @Column(name = "last_sent_time")
    private LocalDateTime lastSentTime;

    @Column(name = "status")
    private String status = "PENDING"; // PENDING, ACTIVE, COMPLETED, EXPIRED

    public NotificationTask() {
        // Пустой конструктор для JPA
    }

    public NotificationTask(Long chatId, String notificationText, LocalDateTime notificationDateTime) {
        this.chatId = chatId;
        this.notificationText = notificationText;
        this.notificationDateTime = notificationDateTime;
        this.createdAt = LocalDateTime.now();
        this.status = "PENDING";
        this.sendCount = 0;
    }

    public NotificationTask(Long chatId, String notificationText, LocalDateTime notificationDateTime,
                            String status, Integer sendCount) {
        this.chatId = chatId;
        this.notificationText = notificationText;
        this.notificationDateTime = notificationDateTime;
        this.createdAt = LocalDateTime.now();
        this.status = status;
        this.sendCount = sendCount;
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

    public Integer getSendCount() {
        return sendCount;
    }

    public void setSendCount(Integer sendCount) {
        this.sendCount = sendCount;
    }

    public LocalDateTime getFirstSentTime() {
        return firstSentTime;
    }

    public void setFirstSentTime(LocalDateTime firstSentTime) {
        this.firstSentTime = firstSentTime;
    }

    public LocalDateTime getLastSentTime() {
        return lastSentTime;
    }

    public void setLastSentTime(LocalDateTime lastSentTime) {
        this.lastSentTime = lastSentTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Увеличить счетчик отправок и обновить время последней отправки
     */
    public void incrementSendCount() {
        this.sendCount++;
        this.lastSentTime = LocalDateTime.now();

        // При первой отправке устанавливаем firstSentTime
        if (this.firstSentTime == null) {
            this.firstSentTime = LocalDateTime.now();
            this.status = "ACTIVE";
        }
    }

    /**
     * Проверить, можно ли отправлять напоминание (не превышен лимит в 6 отправок)
     */
    public boolean canSend() {
        return sendCount < 6 && !"COMPLETED".equals(status) && !"EXPIRED".equals(status) && !"CANCELLED".equals(status);
    }

    /**
     * Проверить, истекло ли напоминание (создано более 1 часа назад)
     */
    public boolean isExpired() {
        return createdAt.plusHours(1).isBefore(LocalDateTime.now());
    }

    /**
     * Пометить как выполненное
     */
    public void markAsCompleted() {
        this.status = "COMPLETED";
    }

    /**
     * Пометить как истекшее
     */
    public void markAsExpired() {
        this.status = "EXPIRED";
    }

    /**
     * Пометить как отмененное
     */
    public void markAsCancelled() {
        this.status = "CANCELLED";
    }

    @Override
    public String toString() {
        return "NotificationTask{" +
                "id=" + id +
                ", chatId=" + chatId +
                ", notificationText='" + notificationText + '\'' +
                ", notificationDateTime=" + notificationDateTime +
                ", createdAt=" + createdAt +
                ", firstSentTime=" + firstSentTime +
                ", sendCount=" + sendCount +
                ", lastSentTime=" + lastSentTime +
                ", status='" + status + '\'' +
                '}';
    }
}
