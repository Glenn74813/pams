package com.avtech.pams.inventory;

import com.fasterxml.jackson.annotation.JsonValue;

/** On-hand level against the item's minimum (reorder point) and maximum. */
public enum StockStatus {
    OUT, LOW, OK, OVER;

    @JsonValue
    public String key() {
        return name().toLowerCase();
    }
}
