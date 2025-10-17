package pro.sky.telegrambot.model;

import pro.sky.telegrambot.model.enums.SendingPhase;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table (name = "notification_sending_state")
public class NotificationSendingState {
    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne (fetch = FetchType.LAZY)
    @JoinColumn (name = "instance_id", nullable = false, unique = true)
    private NotificationInstance instance;

    @Column (name = "first_sent_time")
    private LocalDateTime firstSentTime;

    @Column(name = "last_sent_time")
    private LocalDateTime lastSentTime;

    @Column (name = "send_count", nullable = false)
    private Integer sendCount =0;

    @Column (name = "max_attempts", nullable = false)
    private Integer maxAttempts = 6;

    @Column (name = "next_scheduled_send")
    private LocalDateTime nextScheduledSend;

    @Enumerated(EnumType.STRING)
    @Column (name = "sending_phase", nullable = false)
    private SendingPhase sendingPhase = SendingPhase.INITIAL;

    @Column (name = "pause_until")
    private LocalDateTime pauseUntil;

    public NotificationSendingState() {
    }

    public NotificationSendingState(NotificationInstance instance) {
        this.instance = instance;
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

    public Integer getSendCount() {
        return sendCount;
    }

    public void setSendCount(Integer sendCount) {
        this.sendCount = sendCount;
    }

    public Integer getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(Integer maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public LocalDateTime getNextScheduledSend() {
        return nextScheduledSend;
    }

    public void setNextScheduledSend(LocalDateTime nextScheduledSend) {
        this.nextScheduledSend = nextScheduledSend;
    }

    public SendingPhase getSendingPhase() {
        return sendingPhase;
    }

    public void setSendingPhase(SendingPhase sendingPhase) {
        this.sendingPhase = sendingPhase;
    }

    public LocalDateTime getPauseUntil() {
        return pauseUntil;
    }

    public void setPauseUntil(LocalDateTime pauseUntil) {
        this.pauseUntil = pauseUntil;
    }

    public void incrementSendCount() {
        this.sendCount++;
        this.lastSentTime = LocalDateTime.now();
    }

    public boolean isPaused() {
        return pauseUntil != null&& LocalDateTime.now().isAfter(pauseUntil);
    }

    public boolean canSend(){
        return !isPaused() && sendCount < maxAttempts;
    }

    @Override
    public String toString() {
        return "NotificationSendingState{" +
                "id=" + id +
                ", instanceId=" + (instance !=null?instance.getId():"null") +
                ", firstSentTime=" + firstSentTime +
                ", lastSentTime=" + lastSentTime +
                ", sendCount=" + sendCount +
                ", maxAttempts=" + maxAttempts +
                ", nextScheduledSend=" + nextScheduledSend +
                ", sendingPhase=" + sendingPhase +
                ", pauseUntil=" + pauseUntil +
                '}';
    }
}
