package pro.sky.telegrambot.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table (name = "notification_delivery")
public class NotificationDelivery {
   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @ManyToOne (fetch = FetchType.LAZY)
    @JoinColumn(name = "instance_id", nullable = false)
    private NotificationInstance instance;

   @Column (name = "delivery_time", nullable = false)
    private LocalDateTime deliveryTime;

   @Column (name = "attempt_number", nullable = false)
    private Integer attemptNumber;

   @Column (name = "success", nullable = false)
    private Boolean success =true;

   @Column (name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

   @Column (name = "message_id")
    private Long messageId;

   @Column(name = "delivery_duration_ms")
    private Integer deliveryDurationMs;

    public NotificationDelivery() {
    }

    public NotificationDelivery(NotificationInstance instance, Integer attemptNumber) {
        this.instance = instance;
        this.attemptNumber = attemptNumber;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public NotificationInstance getInstance() {
        return instance;
    }

    public void setInstance(NotificationInstance instance) {
        this.instance = instance;
    }

    public LocalDateTime getDeliveryTime() {
        return deliveryTime;
    }

    public void setDeliveryTime(LocalDateTime deliveryTime) {
        this.deliveryTime = deliveryTime;
    }

    public Integer getAttemptNumber() {
        return attemptNumber;
    }

    public void setAttemptNumber(Integer attemptNumber) {
        this.attemptNumber = attemptNumber;
    }

    public Boolean getSuccess() {
        return success;
    }

    public void setSuccess(Boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public Long getMessageId() {
        return messageId;
    }

    public void setMessageId(Long messageId) {
        this.messageId = messageId;
    }

    public Integer getDeliveryDurationMs() {
        return deliveryDurationMs;
    }

    public void setDeliveryDurationMs(Integer deliveryDurationMs) {
        this.deliveryDurationMs = deliveryDurationMs;
    }

    @Override
    public String toString() {
        return "NotificationDelivery{" +
                "id=" + id +
                ", instanceId=" + (instance !=null?instance.getId():"null") +
                ", deliveryTime=" + deliveryTime +
                ", attemptNumber=" + attemptNumber +
                ", success=" + success +
                ", errorMessage='" + errorMessage + '\'' +
                ", messageId=" + messageId +
                ", deliveryDurationMs=" + deliveryDurationMs +
                '}';
    }
}
