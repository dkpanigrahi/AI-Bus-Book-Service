package com.demo.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/**
 * Published by BookingService when seats are successfully held.
 *
 * Now carries:
 *  - bookingGroupId  (replaces single bookingId — one event per group)
 *  - boarding/alighting stop info  (so downstream can show segment details)
 *  - per-seat detail list  (seatNumber, label, passenger, price per seat)
 *  - totalAmount  (pre-computed sum of all seat prices)
 *
 * Payment Service listens on booking.created and initiates the charge.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingCreatedEvent {

    private Integer bookingId;
    private String eventId;             // = BookingGroup.sagaEventId (UUID)
    private Integer bookingGroupId;
    private Integer userId;
    private String userEmail;
    private Integer busId;
    private String busNo;

    private LocalDate journeyDate;

    private Integer boardingStopSequence;
    private String boardingStopName;
    private Integer alightingStopSequence;
    private String alightingStopName;

    /** One entry per seat held in this group. */
    private List<SeatBookingDetail> seats;

    private Double totalAmount;
    private String status;              // always "PENDING" when emitted

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatBookingDetail {
        private Integer bookingId;       // child Booking.id
        private Integer seatNumber;
        private String seatLabel;
        private String seatType;
        private String deckLevel;
        private String passengerName;
        private Integer passengerAge;
        private String passengerGender;
        private Double price;            // final price for this seat
    }
}