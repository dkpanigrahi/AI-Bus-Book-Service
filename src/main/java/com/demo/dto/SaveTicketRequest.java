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
public class SaveTicketRequest {
    private String date;
    private Integer busId;
    private List<String> passengerName;
    private List<Integer> seatno;
    private List<Integer> bookingids;
    private String transactionId;
}