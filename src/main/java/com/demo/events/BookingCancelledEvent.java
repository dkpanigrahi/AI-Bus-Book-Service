package com.demo.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Published when a BookingGroup is cancelled (payment failure, user cancel, or expiry).
 * Carries both group-level and individual booking IDs for downstream consumers
 * (e.g. refund service, notification service).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCancelledEvent {
    private String eventId;
    private String sagaEventId;          // links back to BookingCreatedEvent.eventId
    private Integer bookingGroupId;
    private List<Integer> bookingIds;    // individual Booking.id values
    private Integer userId;
    private String userEmail;
    private String reason;
}