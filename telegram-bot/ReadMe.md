# Telegram Bot - Напоминания

Spring Boot приложение для управления напоминаниями через Telegram бота.

## Быстрый старт

### 1. Предварительные требования
- Java 11+
- Maven 3.6+
- PostgreSQL 12+
- Telegram бот (получить токен у @BotFather)
- Создайте базу данных в PostgreSQL, укажите логин и пароль 

### 2. Настройка окружения
- Создайте .env файл из шаблона (см. раздел Для Windows PowerShell)

### 3. Запуск приложения в IntelliJ IDEA:
- нажмите правой кнопкой мыши на зелёную стрелку TelegramBotApplication
- выберете Modify Run Configuration
- в Modify options поставьте галочку напротив Enviroment variables
- в появившемся окне "Enviroment variables" укажите путь к файлу config.env.dev

#### Для Windows PowerShell:
```bash
  #Создайте .env файл из шаблона
Copy-Item configuration.env.example config.env.dev

# 2. Отредактируйте .env файл
# Отредактируйте файл с вашими настройками:
notepad config.env.dev  # или используйте любой текстовый редактор 
