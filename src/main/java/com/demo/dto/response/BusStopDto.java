package com.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class BusStopDto {
    int id;
    int stopSequence;
    String stopName;
    String city;
    String arrivalTime;
    String departureTime;
    int priceFromOrigin;
    String platformNo;
}