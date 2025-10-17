package pro.sky.telegrambot.model;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table (name = "chat")
public class Chat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column (name = "chat_id", unique = true,nullable = false)
    private Long chatId;

    @Column (name = "username")
    private String username;

    @Column (name = "first_name")
    private String firstName;

    @Column (name = "created_at", nullable = false, unique = false)
    private LocalDateTime createdAt=LocalDateTime.now();

    @Column (name = "last_activity", nullable = false)
    private LocalDateTime lastActivity=LocalDateTime.now();

    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<NotificationTemplate> templates = new ArrayList<>();

    @OneToMany(mappedBy = "chat", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<NotificationInstance> instances = new ArrayList<>();

    public Chat() {
    }

    public Chat(Long chatId,String username, String firstName) {
        this.username = username;
        this.chatId = chatId;
        this.firstName = firstName;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChatId() {
        return chatId;
    }

    public void setChatId(Long chatId) {
        this.chatId = chatId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getLastActivity() {
        return lastActivity;
    }

    public void setLastActivity(LocalDateTime lastActivity) {
        this.lastActivity = lastActivity;
    }

    public List<NotificationTemplate> getTemplates() {
        return templates;
    }

    public void setTemplates(List<NotificationTemplate> templates) {
        this.templates = templates;
    }

    public List<NotificationInstance> getInstances() {
        return instances;
    }

    public void setInstances(List<NotificationInstance> instances) {
        this.instances = instances;
    }

    @Override
    public String toString() {
        return "Chat{" +
                "id=" + id +
                ", chatId=" + chatId +
                ", username='" + username + '\'' +
                ", firstName='" + firstName + '\'' +
                ", createdAt=" + createdAt +
                ", lastActivity=" + lastActivity +
                '}';
    }
}
