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
public class PaymentProcessingEvent {
    private String eventId;
    private String bookingEventId;
    private Integer userId;
    private String userEmail;
    private Double amount;
    private String currency;
    private List<Integer> bookingIds;
    private LocalDate journeyDate;
    private Integer busId;
}