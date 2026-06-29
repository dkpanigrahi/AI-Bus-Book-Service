package com.demo.dto;

import com.demo.enums.BookingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class BookingDto {

    private Integer id;
    private Integer bookingGroupId;

    private Integer busId;
    private Integer userId;

    private LocalDate journeyDate;
    private String boardingStopName;
    private String alightingStopName;

    // Seat snapshot
    private Integer seatNumber;
    private String seatLabel;
    private String seatType;
    private String deckLevel;
    private boolean isSleeper;

    // Passenger
    private String passengerName;
    private Integer passengerAge;
    private String passengerGender;

    // Pricing
    private Double finalPrice;

    // Status
    private BookingStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
}