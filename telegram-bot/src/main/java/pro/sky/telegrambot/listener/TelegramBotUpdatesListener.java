package pro.sky.telegrambot.listener;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.List;

@Service
public class TelegramBotUpdatesListener implements UpdatesListener {

    private Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);

    @Autowired
    private TelegramBot telegramBot;

    @PostConstruct
    public void init() {
        telegramBot.setUpdatesListener(this);
    }

    @Override
    public int process(List<Update> updates) {
        updates.forEach(update -> {
            logger.info("Processing update: {}", update);
            // Проверяем, что сообщение не пустое и содержит текст
            if (update.message()!=null&&update.message().text()!=null) {
                String messageText = update.message().text();
                Long chatId = update.message().chat().id();
                // Обрабатываем команду /start
                if("/start".equals(messageText)){
                    // Создаем и отправляем приветственное сообщение
                    SendMessage greeting = new SendMessage(chatId, "Привет! " +
                            "Я твой телеграмм бот. Рад приветствовать!");
                    telegramBot.execute(greeting);
                    logger.info(" Send greeting message to chat: {}", chatId);
                }
            }
        });
        return UpdatesListener.CONFIRMED_UPDATES_ALL;
    }
}
