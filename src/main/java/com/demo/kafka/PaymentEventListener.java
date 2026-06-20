package com.demo.kafka;

import com.demo.events.PaymentCompletedEvent;
import com.demo.service.BookingSagaOrchestrator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class PaymentEventListener {

    @Autowired
    private BookingSagaOrchestrator sagaOrchestrator;

    /**
     * Listens for PaymentCompletedEvent from Payment Service.
     * This is step 2 of the Saga Choreography pattern.
     * On success  → confirm booking + send notification
     * On failure  → cancel booking (compensation) + send failure notification
     */
    @KafkaListener(
        topics = "${kafka.topics.payment-completed:payment.completed}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handlePaymentCompletedEvent(
            @Payload PaymentCompletedEvent event,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        log.info("Received PaymentCompletedEvent - EventId: {}, Success: {}, " +
                 "TransactionId: {}, BookingIds: {}, Topic: {}, Partition: {}, Offset: {}",
                event.getEventId(), event.isSuccess(), event.getTransactionId(),
                event.getBookingIds(), topic, partition, offset);

        try {
            if (event.isSuccess()) {
                log.info("Payment successful for eventId: {}. Confirming bookings.", event.getEventId());
                sagaOrchestrator.handlePaymentSuccess(event);
            } else {
                log.warn("Payment failed for eventId: {}. Reason: {}. Initiating compensation.",
                        event.getEventId(), event.getErrorMessage());
                sagaOrchestrator.handlePaymentFailure(event);
            }
        } catch (Exception e) {
            log.error("Error processing PaymentCompletedEvent for eventId: {}. Error: {}",
                    event.getEventId(), e.getMessage(), e);
        }
    }
}