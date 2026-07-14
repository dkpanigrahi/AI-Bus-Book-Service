package com.demo.dto.response;

import com.demo.enums.DeckLevel;
import com.demo.enums.PriceAdjustmentType;
import com.demo.enums.SeatType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SeatTypePricingDto {
    int id;
    SeatType seatType;
    DeckLevel deckLevel;
    PriceAdjustmentType adjustmentType;
    int adjustmentValue;
    String label;
}