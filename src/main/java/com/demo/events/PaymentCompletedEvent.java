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
public class PaymentCompletedEvent {
    private String eventId;
    private String paymentEventId;
    private boolean success;
    private String transactionId;
    private String errorMessage;
    private Integer userId;
    private String userEmail;
    private List<Integer> bookingIds;
    private LocalDate journeyDate;
    private Integer busId;
    private List<String> passengerNames;
    private List<Integer> seatNumbers;
}