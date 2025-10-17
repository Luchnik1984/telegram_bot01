package pro.sky.telegrambot.service;

import pro.sky.telegrambot.model.NotificationDelivery;
import pro.sky.telegrambot.model.NotificationInstance;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pro.sky.telegrambot.repository.NotificationDeliveryRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class NotificationDeliveryService {

    private static final Logger logger = LoggerFactory.getLogger(NotificationDeliveryService.class);

    private final NotificationDeliveryRepository deliveryRepository;

    public NotificationDeliveryService(NotificationDeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    /**
     * Записать попытку отправки
     */
    public NotificationDelivery recordDeliveryAttempt(NotificationInstance instance,
                                                      Integer attemptNumber,
                                                      boolean success,
                                                      String errorMessage,
                                                      Long messageId,
                                                      Integer deliveryDurationMs) {
        logger.info("Recording delivery attempt for instance: {}, attempt: {}, success: {}",
                instance.getId(), attemptNumber, success);

        NotificationDelivery delivery = new NotificationDelivery(instance, attemptNumber);
        delivery.setSuccess(success);
        delivery.setErrorMessage(errorMessage);
        delivery.setMessageId(messageId);
        delivery.setDeliveryDurationMs(deliveryDurationMs);

        NotificationDelivery savedDelivery = deliveryRepository.save(delivery);
        logger.debug("Recorded delivery: {}", savedDelivery.getId());
        return savedDelivery;
    }

    /**
     * Записать успешную отправку
     */
    public NotificationDelivery recordSuccessfulDelivery(NotificationInstance instance,
                                                         Integer attemptNumber,
                                                         Long messageId,
                                                         Integer deliveryDurationMs) {
        return recordDeliveryAttempt(instance, attemptNumber, true, null,
                messageId, deliveryDurationMs);
    }

    /**
     * Записать неудачную отправку
     */
    public NotificationDelivery recordFailedDelivery(NotificationInstance instance,
                                                     Integer attemptNumber,
                                                     String errorMessage) {
        return recordDeliveryAttempt(instance, attemptNumber, false, errorMessage,
                null, null);
    }

    /**
     * Получить историю отправок для экземпляра
     */
    public List<NotificationDelivery> getDeliveryHistory(NotificationInstance instance) {
        return deliveryRepository.findByInstanceOrderByDeliveryTimeDesc(instance);
    }

    /**
     * Получить количество успешных отправок для экземпляра
     */
    public Long getSuccessfulDeliveryCount(NotificationInstance instance) {
        return deliveryRepository.countSuccessfulDeliveries(instance);
    }

    /**
     * Получить статистику доставки для чата
     */
    public Double getAverageDeliveryTime(Long chatId) {
        Double avgTime = deliveryRepository.getAverageDeliveryTimeByChatId(chatId);
        return avgTime != null ? avgTime : 0.0;
    }

    /**
     * Получить последнюю отправку для экземпляра
     */
    public Optional<NotificationDelivery> getLastDelivery(NotificationInstance instance) {
        return deliveryRepository.findLastDeliveryForInstance(instance);
    }

    /**
     * Получить отправки за период
     */
    public List<NotificationDelivery> getDeliveriesInPeriod(LocalDateTime start, LocalDateTime end) {
        return deliveryRepository.findByDeliveryTimeBetween(start, end);
    }

    /**
     * Получить общую статистику доставки
     */
    public DeliveryStatistics getDeliveryStatistics(LocalDateTime start, LocalDateTime end) {
        List<NotificationDelivery> deliveries = getDeliveriesInPeriod(start, end);

        long total = deliveries.size();
        long successful = deliveries.stream().filter(NotificationDelivery::getSuccess).count();
        double successRate = total > 0 ? (double) successful / total * 100 : 0.0;

        double avgDuration = deliveries.stream()
                .filter(d -> d.getDeliveryDurationMs() != null)
                .mapToInt(NotificationDelivery::getDeliveryDurationMs)
                .average()
                .orElse(0.0);

        return new DeliveryStatistics(total, successful, successRate, avgDuration);
    }

    /**
     * DTO для статистики доставки
     */
    public static class DeliveryStatistics {
        private final long totalDeliveries;
        private final long successfulDeliveries;
        private final double successRate;
        private final double averageDurationMs;

        public DeliveryStatistics(long totalDeliveries, long successfulDeliveries,
                                  double successRate, double averageDurationMs) {
            this.totalDeliveries = totalDeliveries;
            this.successfulDeliveries = successfulDeliveries;
            this.successRate = successRate;
            this.averageDurationMs = averageDurationMs;
        }

        public long getTotalDeliveries() {
            return totalDeliveries;
        }

        public long getSuccessfulDeliveries() {
            return successfulDeliveries;
        }

        public double getSuccessRate() {
            return successRate;
        }

        public double getAverageDurationMs() {
            return averageDurationMs;
        }

        @Override
        public String toString() {
            return String.format("DeliveryStats{total=%d, success=%d, rate=%.2f%%, avgDuration=%.2fms}",
                    totalDeliveries, successfulDeliveries, successRate, averageDurationMs);
        }
    }
}
