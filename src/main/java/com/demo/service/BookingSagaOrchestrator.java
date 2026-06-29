package com.demo.service;

import com.demo.entity.BookingGroup;
import com.demo.events.*;
import com.demo.kafka.BookingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Saga Orchestrator — coordinates the booking lifecycle across services.
 *
 * Redesigned changes:
 *  - Resolves BookingGroup by sagaEventId (not raw booking ID list)
 *  - Delegates all state mutations to BookingService (single source of truth)
 *  - Builds richer NotificationEvent with segment + seat breakdown
 */
@Service
@Slf4j
public class BookingSagaOrchestrator {

    private final BookingService bookingService;
    private final BookingEventPublisher eventPublisher;

    public BookingSagaOrchestrator(@Lazy BookingService bookingService,
                                   BookingEventPublisher eventPublisher) {
        this.bookingService = bookingService;
        this.eventPublisher = eventPublisher;
    }

    // ── Step 1: Initiate ──────────────────────────────────────────────────────

    public void initiateBookingSaga(BookingCreatedEvent event) {
        log.info("=== SAGA STARTED === eventId={} groupId={} seats={}",
                event.getEventId(), event.getBookingGroupId(),
                event.getSeats() != null ? event.getSeats().size() : 0);
        eventPublisher.publishBookingCreatedEvent(event);
    }

    // ── Step 3a: Payment success ──────────────────────────────────────────────

    public void handlePaymentSuccess(PaymentCompletedEvent event) {
        log.info("=== SAGA SUCCESS === eventId={} transactionId={}",
                event.getEventId(), event.getTransactionId());

        BookingGroup group = bookingService.confirmGroup(event.getEventId(), event.getTransactionId());

        NotificationEvent notification = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type("BOOKING_CONFIRMED")
                .userId(group.getUserId())
                .userEmail(group.getUserEmail())
                .bookingGroupId(group.getId())
                .bookingIds(group.getBookings().stream().map(b -> b.getId()).toList())
                .subject("Booking Confirmed – Your ticket is ready!")
                .message(buildConfirmationMessage(group, event.getTransactionId()))
                .build();

        eventPublisher.publishNotificationEvent(notification);
        log.info("=== SAGA COMPLETED SUCCESSFULLY === eventId={}", event.getEventId());
    }

    // ── Step 3b: Payment failure — compensation ───────────────────────────────

    public void handlePaymentFailure(PaymentCompletedEvent event) {
        log.warn("=== SAGA COMPENSATION === eventId={} reason={}",
                event.getEventId(), event.getErrorMessage());

        BookingGroup group = bookingService.cancelGroupByEventId(
                event.getEventId(), "Payment failed: " + event.getErrorMessage());

        // Downstream cleanup event
        BookingCancelledEvent cancelEvent = BookingCancelledEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .sagaEventId(event.getEventId())
                .bookingGroupId(group.getId())
                .bookingIds(group.getBookings().stream().map(b -> b.getId()).toList())
                .userId(group.getUserId())
                .userEmail(group.getUserEmail())
                .reason("Payment failed: " + event.getErrorMessage())
                .build();
        eventPublisher.publishBookingCancelledEvent(cancelEvent);

        // Notify user
        NotificationEvent notification = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type("PAYMENT_FAILED")
                .userId(group.getUserId())
                .userEmail(group.getUserEmail())
                .bookingGroupId(group.getId())
                .subject("Payment Failed – Booking Not Confirmed")
                .message("We're sorry, your payment could not be processed. " +
                        "Reason: " + event.getErrorMessage() +
                        ". Your seats have been released. Please try again.")
                .build();
        eventPublisher.publishNotificationEvent(notification);

        log.warn("=== SAGA COMPENSATION COMPLETED === eventId={}", event.getEventId());
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private String buildConfirmationMessage(BookingGroup group, String transactionId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Your booking has been confirmed!\n\n");
        sb.append("Transaction ID: ").append(transactionId).append("\n");
        sb.append("Bus: ").append(group.getBusNo()).append("\n");
        sb.append("Journey Date: ").append(group.getJourneyDate()).append("\n");
        sb.append("From: ").append(group.getBoardingStopName())
                .append("  →  To: ").append(group.getAlightingStopName()).append("\n\n");
        sb.append("Passengers:\n");
        group.getBookings().forEach(b ->
                sb.append("  - ").append(b.getPassengerName())
                        .append(" | Seat: ").append(b.getSeatLabel())
                        .append(" (").append(b.getSeatType()).append(", ").append(b.getDeckLevel()).append(")")
                        .append(" | ₹").append(b.getFinalPrice()).append("\n")
        );
        sb.append("\nTotal: ₹").append(group.getTotalAmount());
        sb.append("\n\nThank you for booking with us!");
        return sb.toString();
    }
}