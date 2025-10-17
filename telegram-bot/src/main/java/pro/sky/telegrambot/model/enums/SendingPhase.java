package pro.sky.telegrambot.model.enums;

public enum SendingPhase {
    INITIAL,      // Первоначальная отправка
    REPEAT,       // Повторные отправки
    COMPLETED,    // Отправки завершены
    PAUSED        // На паузе
}
