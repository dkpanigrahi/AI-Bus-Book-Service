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
public class BusDto {
    private int id;
    private String busNo;
    private String startPlace;
    private String destination;
    private String departureTime;
    private boolean availableEveryDay;
    private String coach;
    private List<String> specificDays;
    private int totalSeats;
    private int ticketPrice;
    private DriverDto driver;
    private ConductorDto conductor;
}