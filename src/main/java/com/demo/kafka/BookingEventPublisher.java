package com.demo.kafka;

import com.demo.events.BookingCancelledEvent;
import com.demo.events.BookingCreatedEvent;
import com.demo.events.NotificationEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@Slf4j
public class BookingEventPublisher {

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.booking-created:booking.created}")
    private String bookingCreatedTopic;

    @Value("${kafka.topics.notification-send:notification.send}")
    private String notificationSendTopic;

    @Value("${kafka.topics.booking-cancelled:booking.cancelled}")
    private String bookingCancelledTopic;

    public void publishBookingCreatedEvent(BookingCreatedEvent event) {
        log.info("Publishing BookingCreatedEvent for bookingId: {} to topic: {}",
                event.getBookingId(), bookingCreatedTopic);

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(bookingCreatedTopic, event.getEventId(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("BookingCreatedEvent published successfully. EventId: {}, Offset: {}",
                        event.getEventId(), result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish BookingCreatedEvent for eventId: {}. Error: {}",
                        event.getEventId(), ex.getMessage(), ex);
            }
        });
    }

    public void publishNotificationEvent(NotificationEvent event) {
        log.info("Publishing NotificationEvent of type: {} for userId: {} to topic: {}",
                event.getType(), event.getUserId(), notificationSendTopic);

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(notificationSendTopic, event.getEventId(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("NotificationEvent published successfully. EventId: {}", event.getEventId());
            } else {
                log.error("Failed to publish NotificationEvent. EventId: {}. Error: {}",
                        event.getEventId(), ex.getMessage(), ex);
            }
        });
    }

    public void publishBookingCancelledEvent(BookingCancelledEvent event) {
        log.info("Publishing BookingCancelledEvent for bookingIds: {} to topic: {}",
                event.getBookingIds(), bookingCancelledTopic);

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(bookingCancelledTopic, event.getEventId(), event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("BookingCancelledEvent published successfully. EventId: {}", event.getEventId());
            } else {
                log.error("Failed to publish BookingCancelledEvent. EventId: {}. Error: {}",
                        event.getEventId(), ex.getMessage(), ex);
            }
        });
    }
}