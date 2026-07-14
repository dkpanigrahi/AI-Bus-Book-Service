package com.demo.dto.response;

import com.demo.enums.DeckLevel;
import com.demo.enums.PriceAdjustmentType;
import com.demo.enums.SeatType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
 
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TicketPriceCalculationResponse {
    int busId;
    int boardingStopSequence;
    int alightingStopSequence;
    String boardingStopName;
    String alightingStopName;
    int basePrice;
    SeatType seatType;
    DeckLevel deckLevel;
    PriceAdjustmentType adjustmentType;
    int adjustmentValue;
    int finalPrice;
    String priceBreakdown;  // e.g. "Base ₹350 + Window premium ₹50 = ₹400"
}