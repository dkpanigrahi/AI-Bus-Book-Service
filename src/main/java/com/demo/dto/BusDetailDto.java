package com.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusDetailDto {

    private Integer id;
    private String busNo;
    private Integer totalSeats;

    /** Ordered stop list. First = origin, last = destination. */
    private List<StopDto> stops;

    private SeatLayoutDto seatLayout;

    private List<SeatTypePricingDto> seatTypePricings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StopDto {
        private Integer stopSequence;
        private String stopName;
        /** Cumulative price from origin to this stop. */
        private Integer priceFromOrigin;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatLayoutDto {
        private List<SeatDto> seats;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatDto {
        private Integer seatNumber;
        private String seatLabel;
        private String seatType;
        private String deckLevel;
        private boolean isSleeper;
        private boolean isActive;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SeatTypePricingDto {
        private String seatType;
        private String deckLevel;
        private String adjustmentType;
        private int adjustmentValue;
    }
}