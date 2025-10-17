package pro.sky.telegrambot.service;

import pro.sky.telegrambot.model.NotificationInstance;
import pro.sky.telegrambot.model.NotificationSendingState;
import pro.sky.telegrambot.model.enums.SendingPhase;
import pro.sky.telegrambot.repository.NotificationSendingStateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;


@Service
@Transactional
public class NotificationSendingStateService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationSendingStateService.class);

    private final NotificationSendingStateRepository sendingStateRepository;

    public NotificationSendingStateService(NotificationSendingStateRepository sendingStateRepository) {
        this.sendingStateRepository = sendingStateRepository;
    }

    /**
     * Инициализировать состояние отправки для экземпляра
     */
    public NotificationSendingState initializeState(NotificationInstance instance) {
        logger.info("Initializing sending state for instance: {}", instance.getId());

        NotificationSendingState state = new NotificationSendingState(instance);
        NotificationSendingState savedState = sendingStateRepository.save(state);

        logger.debug("Initialized sending state: {}", savedState.getId());
        return savedState;
    }

    /**
     * Обновить состояние после отправки
     */
    public void updateAfterSend(Long instanceId, boolean sendSuccessful) {
        sendingStateRepository.findByInstanceId(instanceId).ifPresent(state -> {
            state.incrementSendCount();
            state.setLastSentTime(LocalDateTime.now());

            // Обновляем фазу отправки
            if (state.getSendCount() == 1) {
                state.setSendingPhase(SendingPhase.REPEAT);
                // Устанавливаем время следующей отправки (через 10 минут)
                state.setNextScheduledSend(LocalDateTime.now().plusMinutes(10));
            } else if (state.getSendCount() >= state.getMaxAttempts()) {
                state.setSendingPhase(SendingPhase.COMPLETED);
                state.setNextScheduledSend(null);
            } else {
                // Устанавливаем следующую отправку через 10 минут
                state.setNextScheduledSend(LocalDateTime.now().plusMinutes(10));
            }

            sendingStateRepository.save(state);
            logger.debug("Updated sending state for instance: {}, send count: {}",
                    instanceId, state.getSendCount());
        });
    }

    /**
     * Завершить отправки для экземпляра
     */
    public void completeSending(Long instanceId) {
        sendingStateRepository.findByInstanceId(instanceId).ifPresent(state -> {
            state.setSendingPhase(SendingPhase.COMPLETED);
            state.setNextScheduledSend(null);
            state.setPauseUntil(null);
            sendingStateRepository.save(state);
            logger.info("Completed sending for instance: {}", instanceId);
        });
    }

    /**
     * Поставить отправки на паузу
     */
    public void pauseSending(Long instanceId, LocalDateTime pauseUntil) {
        sendingStateRepository.pauseSending(instanceId, pauseUntil);
        logger.info("Paused sending for instance: {} until {}", instanceId, pauseUntil);
    }

    /**
     * Возобновить отправки
     */
    public void resumeSending(Long instanceId) {
        sendingStateRepository.resumeSending(instanceId);
        logger.info("Resumed sending for instance: {}", instanceId);
    }

    /**
     * Найти состояния готовые к отправке
     */
    public List<NotificationSendingState> findReadyToSendStates() {
        return sendingStateRepository.findReadyToSendStates(LocalDateTime.now());
    }

    /**
     * Обновить расписание для повторных отправок
     */
    public void updateNextScheduledSends() {
        List<NotificationSendingState> states = sendingStateRepository.findStatesNeedingNextSchedule();

        for (NotificationSendingState state : states) {
            if (state.getLastSentTime() != null) {
                state.setNextScheduledSend(state.getLastSentTime().plusMinutes(10));
                sendingStateRepository.save(state);
                logger.debug("Updated next scheduled send for instance: {} to {}",
                        state.getInstance().getId(), state.getNextScheduledSend());
            }
        }
    }

    /**
     * Автоматически возобновить отправки с истекшей паузой
     */
    public void resumeExpiredPauses() {
        List<NotificationSendingState> expiredPauses = sendingStateRepository.findExpiredPauses(LocalDateTime.now());

        for (NotificationSendingState state : expiredPauses) {
            state.setSendingPhase(SendingPhase.REPEAT);
            state.setPauseUntil(null);
            state.setNextScheduledSend(LocalDateTime.now().plusMinutes(10));
            sendingStateRepository.save(state);
            logger.info("Auto-resumed sending for instance: {} after pause expiration",
                    state.getInstance().getId());
        }
    }

    /**
     * Получить состояние отправки для экземпляра
     */
    public Optional<NotificationSendingState> findByInstance(NotificationInstance instance) {
        return sendingStateRepository.findByInstance(instance);
    }

    /**
     * Получить состояние отправки по ID экземпляра
     */
    public Optional<NotificationSendingState> findByInstanceId(Long instanceId) {
        // Для простоты реализации, здесь нужно создать метод в репозитории
        // В реальной реализации нужно добавить метод в репозиторий
        return Optional.empty(); // Заглушка
    }

    /**
     * Проверить, можно ли отправлять экземпляр
     */
    public boolean canSend(NotificationInstance instance) {
        return findByInstance(instance)
                .map(state -> {
                    boolean canSend = state.getSendCount() < state.getMaxAttempts() &&
                            state.getSendingPhase() != SendingPhase.COMPLETED &&
                            state.getSendingPhase() != SendingPhase.PAUSED &&
                            (state.getPauseUntil() == null ||
                                    LocalDateTime.now().isAfter(state.getPauseUntil()));

                    logger.debug("Can send instance {}: {} (phase: {}, sendCount: {}/{}, paused: {})",
                            instance.getId(), canSend, state.getSendingPhase(),
                            state.getSendCount(), state.getMaxAttempts(),
                            state.getPauseUntil() != null);
                    return canSend;
                })
                .orElse(false);
    }

    /**
     * Получить количество оставшихся отправок
     */
    public int getRemainingAttempts(NotificationInstance instance) {
        return findByInstance(instance)
                .map(state -> state.getMaxAttempts() - state.getSendCount())
                .orElse(0);
    }

    /**
     * Получить информацию о состоянии отправки
     */
    public String getStateInfo(Long instanceId) {
        return findByInstanceId(instanceId)
                .map(state -> String.format(
                        "State: phase=%s, sent=%d/%d, nextSend=%s, pausedUntil=%s",
                        state.getSendingPhase(),
                        state.getSendCount(),
                        state.getMaxAttempts(),
                        state.getNextScheduledSend(),
                        state.getPauseUntil()
                ))
                .orElse("State not found");
    }
}
