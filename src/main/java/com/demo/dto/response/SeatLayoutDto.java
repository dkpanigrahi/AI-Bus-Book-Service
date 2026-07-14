package com.demo.dto.response;

import com.demo.enums.DeckType;
import com.demo.enums.LayoutArrangement;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class SeatLayoutDto {
    int id;
    DeckType deckConfiguration;
    LayoutArrangement lowerDeckArrangement;
    int lowerDeckRows;
    LayoutArrangement upperDeckArrangement;
    Integer upperDeckRows;
    boolean hasRearBenchRow;
    Integer rearBenchSeatCount;
 
    /** Full seat list — FE groups by (deckLevel, rowNumber, columnPosition) to render grid */
    List<SeatDto> seats;
}