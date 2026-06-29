package com.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BusSearchResponseDto {
    private int id;
    private String busNo;
    private String startPlace;
    private String destination;
    private String departureTime;
    private String coach;
    private int totalSeats;
    private int ticketPrice;
    private String driverName;
    private String conductorName;
}