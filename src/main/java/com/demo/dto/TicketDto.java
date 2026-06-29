package com.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketDto {
    private int id;
    private String passengerName;
    private Integer seatNo;
    private LocalDate date;
    private UserDto user;
    private BusDto bus;
    private String transactionId;
}