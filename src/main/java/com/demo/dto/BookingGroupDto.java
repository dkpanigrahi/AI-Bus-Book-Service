package com.demo.dto;

import com.demo.enums.BookingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Response DTO for a full BookingGroup (returned from hold/confirm/cancel endpoints).
 */
@Data
@Builder
public class BookingGroupDto {

    private Integer id;
    private String sagaEventId;

    private Integer userId;
    private String userEmail;
    private Integer busId;
    private String busNo;

    private LocalDate journeyDate;
    private Integer boardingStopSequence;
    private String boardingStopName;
    private Integer alightingStopSequence;
    private String alightingStopName;

    private Double totalAmount;
    private String transactionId;

    private BookingStatus status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;

    /** One entry per seat in this group. */
    private List<BookingDto> bookings;
}