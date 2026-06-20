package com.demo.service;

import com.demo.entity.Booking;
import com.demo.events.BookingCancelledEvent;
import com.demo.events.BookingCreatedEvent;
import com.demo.events.NotificationEvent;
import com.demo.events.PaymentCompletedEvent;
import com.demo.kafka.BookingEventPublisher;
import com.demo.repository.BookingRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Saga Choreography Orchestrator for the booking flow.
 *
 * Flow:
 *  1. BookingService.holdSeats()         → publishes BookingCreatedEvent
 *  2. PaymentService                     → listens, processes payment, publishes PaymentCompletedEvent
 *  3. BookingSagaOrchestrator            → listens for PaymentCompletedEvent:
 *       SUCCESS → confirm bookings → publish NotificationEvent (BOOKING_CONFIRMED)
 *       FAILURE → cancel bookings → publish NotificationEvent (PAYMENT_FAILED)
 *                                 → publish BookingCancelledEvent (compensation)
 */
@Service
@Slf4j
public class BookingSagaOrchestrator {

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private BookingEventPublisher eventPublisher;

    /**
     * Step 1: Initiate SAGA after seats are held.
     * Publishes BookingCreatedEvent to trigger Payment Service.
     */
    public void initiateBookingSaga(BookingCreatedEvent event) {
        log.info("=== SAGA STARTED === BookingCreatedEvent published. EventId: {}, BookingIds: {}",
                event.getEventId(), event.getBookingId());
        eventPublisher.publishBookingCreatedEvent(event);
    }

    /**
     * Step 3a: Handle payment success.
     * Confirms all bookings and sends a confirmation notification.
     */
    @Transactional
    public void handlePaymentSuccess(PaymentCompletedEvent event) {
        log.info("=== SAGA STEP 3 - PAYMENT SUCCESS === EventId: {}, BookingIds: {}",
                event.getEventId(), event.getBookingIds());

        List<Booking> bookings = bookingRepository.findByIdIn(event.getBookingIds());

        if (bookings.isEmpty()) {
            log.warn("No bookings found for ids: {}", event.getBookingIds());
            return;
        }

        // Confirm bookings
        bookings.forEach(booking -> {
            booking.setBooked(true);
            booking.setInProcess(false);
            booking.setExpirationTime(null);
        });
        bookingRepository.saveAll(bookings);

        log.info("Bookings confirmed successfully for ids: {}", event.getBookingIds());

        // Build and publish BOOKING_CONFIRMED notification
        NotificationEvent notificationEvent = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type("BOOKING_CONFIRMED")
                .userId(event.getUserId())
                .userEmail(event.getUserEmail())
                .bookingIds(event.getBookingIds())
                .passengerNames(event.getPassengerNames())
                .seatNumbers(event.getSeatNumbers())
                .journeyDate(event.getJourneyDate())
                .transactionId(event.getTransactionId())
                .subject("Booking Confirmed – Your ticket is ready!")
                .message(buildConfirmationMessage(event))
                .build();

        eventPublisher.publishNotificationEvent(notificationEvent);

        log.info("=== SAGA COMPLETED SUCCESSFULLY === EventId: {}", event.getEventId());
    }

    /**
     * Step 3b: Handle payment failure — compensation transaction.
     * Cancels all held bookings and notifies the user.
     */
    @Transactional
    public void handlePaymentFailure(PaymentCompletedEvent event) {
        log.warn("=== SAGA STEP 3 - PAYMENT FAILED - COMPENSATION === EventId: {}, BookingIds: {}",
                event.getEventId(), event.getBookingIds());

        List<Booking> bookings = bookingRepository.findByIdIn(event.getBookingIds());

        if (bookings.isEmpty()) {
            log.warn("No bookings found for compensation. Ids: {}", event.getBookingIds());
            return;
        }

        // Compensation: release held seats
        bookingRepository.deleteAll(bookings);
        log.info("Compensation complete: {} bookings released for ids: {}",
                bookings.size(), event.getBookingIds());

        // Publish BookingCancelledEvent for any downstream cleanup
        BookingCancelledEvent cancelledEvent = BookingCancelledEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .bookingIds(event.getBookingIds())
                .userId(event.getUserId())
                .reason("Payment failed: " + event.getErrorMessage())
                .build();
        eventPublisher.publishBookingCancelledEvent(cancelledEvent);

        // Notify user of payment failure
        NotificationEvent notificationEvent = NotificationEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .type("PAYMENT_FAILED")
                .userId(event.getUserId())
                .userEmail(event.getUserEmail())
                .bookingIds(event.getBookingIds())
                .subject("Payment Failed – Booking Not Confirmed")
                .message("We're sorry, your payment could not be processed. " +
                         "Reason: " + event.getErrorMessage() +
                         ". Your seats have been released. Please try again.")
                .build();

        eventPublisher.publishNotificationEvent(notificationEvent);

        log.warn("=== SAGA COMPENSATION COMPLETED === EventId: {}", event.getEventId());
    }

    private String buildConfirmationMessage(PaymentCompletedEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append("Your booking has been confirmed!\n\n");
        sb.append("Transaction ID: ").append(event.getTransactionId()).append("\n");
        sb.append("Journey Date: ").append(event.getJourneyDate()).append("\n");

        if (event.getPassengerNames() != null && event.getSeatNumbers() != null) {
            sb.append("Passengers:\n");
            List<String> names = event.getPassengerNames();
            List<Integer> seats = event.getSeatNumbers();
            for (int i = 0; i < names.size(); i++) {
                sb.append("  - ").append(names.get(i))
                  .append(" (Seat: ").append(i < seats.size() ? seats.get(i) : "N/A").append(")\n");
            }
        }
        sb.append("\nThank you for booking with us!");
        return sb.toString();
    }
}