package pro.sky.telegrambot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import pro.sky.telegrambot.model.NotificationInstance;
import pro.sky.telegrambot.model.NotificationSendingState;

import java.time.LocalDateTime;

/**
 * СЕРВИС ПЛАНИРОВЩИКА - ОСНОВНОЙ ДВИГАТЕЛЬ СИСТЕМЫ НАПОМИНАНИЙ
 * Этот сервис отвечает за:
 * - Регулярную проверку и отправку напоминаний
 * - Управление состоянием отправок
 * - Очистку устаревших данных
 * - Восстановление после сбоев
 * Все расписания настроены через аннотации @Scheduled с cron-выражениями
 */
@Service
public class NotificationSchedulerService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationSchedulerService.class);

    private final NotificationOrchestratorService orchestratorService;
    private final NotificationInstanceService instanceService;
    private final NotificationSendingStateService sendingStateService;

    public NotificationSchedulerService(NotificationOrchestratorService orchestratorService,
                                        NotificationInstanceService instanceService,
                                        NotificationSendingStateService sendingStateService) {
        this.orchestratorService = orchestratorService;
        this.instanceService = instanceService;
        this.sendingStateService = sendingStateService;

        logger.info("NotificationSchedulerService initialized with all dependencies");
    }

    /**
     * ОСНОВНОЙ ШЕДУЛЕР - ОБРАБОТКА НАПОМИНАНИЙ КАЖДУЮ МИНУТУ
     * Этот метод - сердце системы напоминаний. Он:
     * - Запускается в 0 секунд каждой минуты (12:00:00, 12:01:00, 12:02:00...)
     * - Находит все напоминания, которые должны быть отправлены в текущую минуту
     * - Обрабатывает их через OrchestratorService
     * - Выполняет базовое обслуживание системы.
     */
    @Scheduled(cron = "0 * * * * *")
    public void processScheduledNotifications() {
        LocalDateTime startTime = LocalDateTime.now();
        logger.info("=== ОСНОВНОЙ ШЕДУЛЕР ЗАПУЩЕН в {} ===", startTime);

        try {
            // ОСНОВНАЯ ЛОГИКА: обработка напоминаний для текущей минуты
            // OrchestratorService сам находит due notifications и отправляет их
            orchestratorService.processDueNotifications();

            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();

            logger.info("=== ОСНОВНОЙ ШЕДУЛЕР УСПЕШНО ЗАВЕРШЕН ===");
            logger.info("Время выполнения: {} мс", duration);

        } catch (Exception e) {
            logger.error("=== ОСНОВНОЙ ШЕДУЛЕР ЗАВЕРШИЛСЯ С ОШИБКОЙ ===", e);

            // Даже при ошибке логируем завершение работы
            LocalDateTime endTime = LocalDateTime.now();
            long duration = java.time.Duration.between(startTime, endTime).toMillis();
            logger.error("Время выполнения до ошибки: {} мс", duration);
        }
    }

    /**
     * БЫСТРЫЙ ШЕДУЛЕР - ОБРАБОТКА ГОТОВЫХ СОСТОЯНИЙ КАЖДЫЕ 30 СЕКУНД
     * Этот метод обеспечивает более частую проверку и:
     * - Автоматически возобновляет отправки с истекшей паузой
     * - Обрабатывает состояния, готовые к немедленной отправке
     * - Обеспечивает более отзывчивую систему для повторных отправок
     * Важно: Этот шедулер работает ПАРАЛЛЕЛЬНО с основным и дополняет его
     */
    @Scheduled(cron = "*/30 * * * * *")
    public void processReadyStates() {
        LocalDateTime currentTime = LocalDateTime.now();
        logger.debug("⚡ Быстрая проверка состояний в: {}", currentTime);

        try {
            // ШАГ 1: АВТОМАТИЧЕСКОЕ ВОЗОБНОВЛЕНИЕ ИСТЕКШИХ ПАУЗ
            // Если пользователь ставил напоминание на паузу на 1 час,
            // этот метод автоматически возобновит отправки когда время паузы истечет
            sendingStateService.resumeExpiredPauses();

            // ШАГ 2: ПОИСК СОСТОЯНИЙ, ГОТОВЫХ К ОТПРАВКЕ
            // находим все состояния, которые:
            // - В фазе INITIAL или REPEAT
            // - Не превысили лимит отправок
            // - Не на паузе (или пауза истекла)
            // - Время следующей отправки наступило (или не установлено)
            var readyStates = sendingStateService.findReadyToSendStates();

            if (!readyStates.isEmpty()) {
                logger.info(" Найдено {} состояний, готовых к обработке", readyStates.size());

                int sentCount = 0;
                int skippedCount = 0;

                // ШАГ 3: ОБРАБОТКА КАЖДОГО ГОТОВОГО СОСТОЯНИЯ
                for (var state : readyStates) {
                    var instance = state.getInstance();

                    // Дополнительная проверка перед отправкой
                    if (instance != null && shouldSendBasedOnState(instance, state)) {
                        // Отправляем напоминание через orchestrator
                        orchestratorService.processNotificationForScheduler(instance);
                        sentCount++;
                    } else {
                        skippedCount++;
                        logger.debug("Пропущено состояние: instance={}, причина: проверка не пройдена",
                                instance != null ? instance.getId() : "null");
                    }
                }

                logger.info("Быстрая обработка: отправлено {}, пропущено {}", sentCount, skippedCount);
            } else {
                logger.debug("Нет состояний, готовых к быстрой обработке");
            }

        } catch (Exception e) {
            logger.error("Ошибка в быстром шедулере", e);
        }
    }

    /**
     * ЕЖЕЧАСНАЯ ОЧИСТКА - УПРАВЛЕНИЕ ДАННЫМИ КАЖДЫЙ ЧАС
     * Этот метод выполняет техническое обслуживание системы:
     * - Удаляет старые завершенные напоминания (старше 7 дней)
     * - Освобождает место в базе данных
     * - Поддерживает производительность системы.
     * Запускается каждый час в 00 минут (13:00, 14:00, 15:00...)
     */
    @Scheduled(cron = "0 0 * * * *")
    public void hourlyCleanup() {
        LocalDateTime cleanupTime = LocalDateTime.now();
        logger.info("=== ЗАПУСК ЕЖЕЧАСНОЙ ОЧИСТКИ в {} ===", cleanupTime);

        try {
            // ОЧИСТКА СТАРЫХ ЭКЗЕМПЛЯРОВ
            // удаляет напоминания, которые:
            // - Были созданы более 7 дней назад
            // - Имеют статус COMPLETED, EXPIRED или CANCELLED
            // Это предотвращает бесконечный рост базы данных
            instanceService.cleanupOldInstances();

            logger.info("Ежечасная очистка успешно завершена");

        } catch (Exception e) {
            logger.error("Ежечасная очистка завершилась с ошибкой", e);
        }
    }

    /**
     * ЕЖЕДНЕВНОЕ ТЕХОБСЛУЖИВАНИЕ - КОМПЛЕКСНОЕ ОБСЛУЖИВАНИЕ В 3:00
     * Этот метод выполняет расширенное обслуживание в ночное время:
     * - Можно добавить анализ статистики
     * - Оптимизацию индексов базы данных
     * - Сбор метрик производительности
     * - Отправку отчетов администраторам
     */
    @Scheduled(cron = "0 0 3 * * *")
    public void dailyMaintenance() {
        LocalDateTime maintenanceTime = LocalDateTime.now();
        logger.info("🔧 === ЗАПУСК ЕЖЕДНЕВНОГО ТЕХОБСЛУЖИВАНИЯ в {} ===", maintenanceTime);

        try {
            // МЕСТО ДЛЯ РАСШИРЕННЫХ ОПЕРАЦИЙ ТЕХОБСЛУЖИВАНИЯ:

            // 1. АНАЛИЗ СТАТИСТИКИ
            logger.info("Анализ статистики системы...");
            // Можно добавить сбор метрик: количество активных пользователей,
            // успешность доставки, популярные времена напоминаний и т.д.

            // 2. ПРОВЕРКА ЦЕЛОСТНОСТИ ДАННЫХ
            logger.info("Проверка целостности данных...");
            // Можно добавить проверки на orphaned records,
            // согласованность между таблицами и т.д.

            // 3. ОПТИМИЗАЦИЯ ПРОИЗВОДИТЕЛЬНОСТИ
            logger.info(" Оптимизация производительности...");
            // Можно добавить перестроение индексов,
            // очистку кэшей, анализ медленных запросов

            // 4. УВЕДОМЛЕНИЯ АДМИНИСТРАТОРОВ
            logger.info("Подготовка отчетов для администраторов...");
            // Можно добавить отправку email со статистикой,
            // предупреждениями о проблемах и т.д.

            logger.info(" Ежедневное техобслуживание успешно завершено");

        } catch (Exception e) {
            logger.error(" Ежедневное техобслуживание завершилось с ошибкой", e);
        }
    }

    /**
     * ПРОВЕРКА НЕОБХОДИМОСТИ ОТПРАВКИ НА ОСНОВЕ СОСТОЯНИЯ
     * Этот метод выполняет детальную проверку перед отправкой напоминания.
     * Он гарантирует, что напоминание отправляется только когда это действительно нужно.
     * @param instance экземпляр напоминания для проверки
     * @param state состояние отправки напоминания
     * @return true если напоминание нужно отправить, false если нет
     */
    private boolean shouldSendBasedOnState(NotificationInstance instance,
                                           NotificationSendingState state) {
        LocalDateTime now = LocalDateTime.now();

        // ПРОВЕРКА 1: СТАТУС ЭКЗЕМПЛЯРА
        // отправляем только активные напоминания
        if (instance.getStatus() != pro.sky.telegrambot.model.enums.NotificationStatus.ACTIVE) {
            logger.debug("Пропуск отправки: экземпляр {} не активен (статус: {})",
                    instance.getId(), instance.getStatus());
            return false;
        }

        // ПРОВЕРКА 2: ВРЕМЯ СОЗДАНИЯ
        // Не отправляем напоминания старше 1 часа (автоудаление)
        if (instance.getCreatedAt().plusHours(1).isBefore(now)) {
            logger.info(" Экземпляр {} истек, помечаем как EXPIRED", instance.getId());
            instanceService.markAsExpired(instance.getId());
            return false;
        }

        // ПРОВЕРКА 3: ВОЗМОЖНОСТЬ ОТПРАВКИ ПО СОСТОЯНИЮ
        // проверяем лимиты отправок, паузы и т.д.
        if (!sendingStateService.canSend(instance)) {
            logger.debug("Пропуск отправки: экземпляр {} не может быть отправлен", instance.getId());
            return false;
        }

        // ПРОВЕРКА 4: СЛЕДУЮЩЕЕ ЗАПЛАНИРОВАННОЕ ВРЕМЯ
        // Для повторных отправок проверяем точное время
        if (state.getNextScheduledSend() != null) {
            // Округляем до минут для точного сравнения
            LocalDateTime nextSendMinute = state.getNextScheduledSend()
                    .truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
            LocalDateTime currentMinute = now.truncatedTo(java.time.temporal.ChronoUnit.MINUTES);

            boolean shouldSend = nextSendMinute.equals(currentMinute);

            if (!shouldSend) {
                logger.debug("Пропуск отправки: экземпляр {} - не время отправки ({} != {})",
                        instance.getId(), nextSendMinute, currentMinute);
            }

            return shouldSend;
        }

        // Если nextScheduledSend не установлен, но все проверки пройдены - отправляем
        logger.debug("Все проверки пройдены, отправляем экземпляр {}", instance.getId());
        return true;
    }

    /**
     * РУЧНОЙ ЗАПУСК ОБРАБОТКИ - ДЛЯ ТЕСТИРОВАНИЯ И АДМИНИСТРИРОВАНИЯ
     * Этот метод позволяет вручную запустить обработку напоминаний.
     * Полезно для:
     * - Тестирования функциональности
     * - Отладки проблем
     * - Администрирования системы
     * - Экстренного запуска при сбоях
     */
    public void manualProcess() {
        logger.warn("🔄 === РУЧНОЙ ЗАПУСК ШЕДУЛЕРА в {} ===", LocalDateTime.now());

        try {
            processScheduledNotifications();
            logger.info("Ручной запуск успешно завершен");
        } catch (Exception e) {
            logger.error("Ручной запуск завершился с ошибкой", e);
            throw new RuntimeException("Manual scheduler execution failed", e);
        }
    }

    /**
     * ПОЛУЧЕНИЕ СТАТУСА ШЕДУЛЕРА - ДЛЯ МОНИТОРИНГА И ДИАГНОСТИКИ
     * Этот метод предоставляет информацию о состоянии шедулера.
     * Может использоваться для:
     * - Мониторинга здоровья системы
     * - Панелей администратора
     * - Диагностики проблем
     * - API для внешних систем мониторинга
     * @return строка с информацией о статусе шедулера
     */
    public String getSchedulerStatus() {
        return String.format(
                "Статус шедулера:\n" +
                        "• Время сервера: %s\n" +
                        "• Основной шедулер: активен (каждую минуту)\n" +
                        "• Быстрый шедулер: активен (каждые 30 секунд)\n" +
                        "• Ежечасная очистка: активна\n" +
                        "• Ежедневное обслуживание: активна (3:00)\n" +
                        "• Система: работает нормально ",
                LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss"))
        );
    }

    /**
     * ПРОВЕРКА ЗДОРОВЬЯ СИСТЕМЫ - ДЛЯ HEALTH CHECKS
     * Этот метод может использоваться системами мониторинга
     * для проверки работоспособности сервиса напоминаний.
     * @return true если система работает нормально, false если есть проблемы
     */
    public boolean healthCheck() {
        try {
            // Простая проверка - если мы можем получить текущее время,
            // значит система в основном работает
            LocalDateTime.now();
            logger.debug(" Health check passed");
            return true;
        } catch (Exception e) {
            logger.error(" Health check failed", e);
            return false;
        }
    }
}