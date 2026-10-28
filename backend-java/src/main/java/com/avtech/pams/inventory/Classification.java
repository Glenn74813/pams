package com.avtech.pams.inventory;

import com.fasterxml.jackson.annotation.JsonValue;

/** COA classification of an item. */
public enum Classification {
    CONSUMABLE("consumable", "Consumable"),
    SEMI_EXPENDABLE("semi_expendable", "Semi-Expendable"),
    PPE("ppe", "PPE");

    private final String key;
    private final String label;

    Classification(String key, String label) {
        this.key = key;
        this.label = label;
    }

    @JsonValue
    public String key() {
        return key;
    }

    public String label() {
        return label;
    }

    public static Classification fromKey(String key) {
        for (Classification c : values()) {
            if (c.key.equals(key)) return c;
        }
        throw new IllegalArgumentException("Unknown classification: " + key);
    }
}
