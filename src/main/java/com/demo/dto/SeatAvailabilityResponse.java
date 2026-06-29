package com.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatAvailabilityResponse {
    private int busId;
    private String date;
    private Map<Integer, Boolean> seatMap;
    private int totalSeats;
    private int availableSeats;
    private Integer boardingStopSequence;
    private Integer alightingStopSequence;
}