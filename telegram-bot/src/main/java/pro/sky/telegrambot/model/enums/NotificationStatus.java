package pro.sky.telegrambot.model.enums;

public enum NotificationStatus {
    PENDING,      // Ожидает первой отправки
    ACTIVE,       // Активно (отправляется)
    COMPLETED,    // Выполнено пользователем
    EXPIRED,      // Истекло (прошло 1 час)
    CANCELLED     // Отменено пользователем
}
