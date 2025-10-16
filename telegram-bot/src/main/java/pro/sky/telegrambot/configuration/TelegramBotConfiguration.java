package pro.sky.telegrambot.configuration;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.DeleteMyCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TelegramBotConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(TelegramBotConfiguration.class);

    @Value("${telegram.bot.token}")
    private String token;

    @Bean
    public TelegramBot telegramBot() {

        if (token == null || token.trim().isEmpty()) {
            logger.error("BOT_TOKEN не настроен! Проверьте .env файл или Environment Variables");
            throw new IllegalStateException("BOT_TOKEN is not configured. Check .env file or IDE settings.");
        }

        logger.info("Telegram Bot инициализирован с токеном: {}...",
                token.substring(0, Math.min(10, token.length())));

        TelegramBot bot = new TelegramBot(token);
        bot.execute(new DeleteMyCommands());
        return bot;
    }
}
