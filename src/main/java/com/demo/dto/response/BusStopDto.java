package com.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BusStopDto {
    int id;
    int stopSequence;
    String stopName;
    String city;
    LocalDateTime arrivalTime;
    LocalDateTime departureTime;
    int priceFromOrigin;
    String platformNo;
}