package com.demo.dto.response;

import com.demo.enums.DeckLevel;
import com.demo.enums.SeatType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SeatDto {
    int id;
    int seatNumber;
    String seatLabel;
    int rowNumber;
    int columnPosition;
    DeckLevel deckLevel;
    SeatType seatType;
    boolean isSleeper;
    boolean isActive;
 
    /**
     * Pre-computed price adjustment for this seat based on SeatTypePricing.
     * Populated by service layer when returning seat grid.
     * FE shows: basePrice + seatPriceAdjustment = totalPrice for this seat.
     */
    int seatPriceAdjustment;
 
    /** Injected by booking service: is this seat taken for the chosen date? */
    boolean isBooked;
}