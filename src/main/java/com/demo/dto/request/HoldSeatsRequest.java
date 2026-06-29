package com.demo.dto.request;

import lombok.Data;
import org.antlr.v4.runtime.misc.NotNull;

import java.time.LocalDate;
import java.util.List;

/**
 * Request body for POST /api/bookings/hold.
 *
 * Now typed (vs the previous raw Map<String,Object>):
 *  - boardingStopSequence + alightingStopSequence let the booking service
 *    compute the exact segment price instead of using a flat busDto.ticketPrice.
 *  - passengerDetails carries per-seat passenger info (name, age, gender).
 */
@Data
public class HoldSeatsRequest {

    @NotNull
    private Integer userId;

    @NotNull
    private Integer busId;

    @NotNull
    private LocalDate journeyDate;

    /** Sequence number of the boarding stop (from BusStop.stopSequence). */
    @NotNull
    private Integer boardingStopSequence;

    /** Sequence number of the alighting stop — must be > boardingStopSequence. */
    @NotNull
    private Integer alightingStopSequence;

    /** One entry per seat being booked. */
    @NotNull
    private List<PassengerSeatRequest> passengers;

    @Data
    public static class PassengerSeatRequest {

        /** Physical seat number from Seat entity (SeatLayout). */
        @NotNull
        private Integer seatNumber;

        private String passengerName;

        private Integer passengerAge;

        private String passengerGender;
    }
}