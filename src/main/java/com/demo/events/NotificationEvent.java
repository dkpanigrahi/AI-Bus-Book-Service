package com.demo.events;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String eventId;
    private String type; // BOOKING_CONFIRMED, PAYMENT_FAILED, BOOKING_CANCELLED
    private Integer userId;
    private String userEmail;
    private String userPhone;
    private String subject;
    private String message;
    private List<Integer> bookingIds;
    private List<String> passengerNames;
    private List<Integer> seatNumbers;
    private LocalDate journeyDate;
    private String busNumber;
    private String transactionId;
    private int bookingGroupId;
}