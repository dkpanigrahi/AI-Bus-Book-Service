package com.demo.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * DTO returned by BusService GET /api/buses/{id} — enriched to include
 * stop prices and seat layout metadata so BookingService can:
 *   (a) compute segment prices without a separate Feign call
 *   (b) snapshot seat label/type/deckLevel at hold time
 *
 * BusService should already have these fields; this DTO exposes what
 * BookingService needs from the existing BusDto / BusSearchResponseDto structure.
 */
@Data
@Builder
public class BusDetailDto {

    private Integer id;
    private String busNo;
    private String busName;
    private String coach;
    private String busType;
    private Integer totalSeats;

    /** Ordered stop list. First = origin, last = destination. */
    private List<StopDto> stops;

    /** Flat seat list from SeatLayout. */
    private SeatLayoutDto seatLayout;

    /** Seat-type price adjustments configured by admin. */
    private List<SeatTypePricingDto> seatTypePricings;

    @Data
    @Builder
    public static class StopDto {
        private Integer stopSequence;
        private String stopName;
        private String city;
        private String arrivalTime;
        private String departureTime;
        /** Cumulative price from origin to this stop (admin-entered). */
        private Integer priceFromOrigin;
    }

    @Data
    @Builder
    public static class SeatLayoutDto {
        private List<SeatDto> seats;
    }

    @Data
    @Builder
    public static class SeatDto {
        private Integer seatNumber;
        private String seatLabel;   // e.g. "L12", "U5"
        private String seatType;    // WINDOW / AISLE / MIDDLE
        private String deckLevel;   // LOWER / UPPER
        private boolean isSleeper;
        private boolean isActive;
    }

    @Data
    @Builder
    public static class SeatTypePricingDto {
        private String seatType;          // enum name
        private String deckLevel;         // enum name
        private String adjustmentType;    // FLAT or PERCENTAGE
        private int adjustmentValue;      // e.g. 50 (₹50 flat) or 10 (10%)
    }
}