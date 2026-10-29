package com.avtech.pams.common;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Stored timestamps are Asia/Manila wall-clock time; the API always sends them with an offset. */
public final class Times {

    public static final String ZONE_ID = "Asia/Manila";
    public static final ZoneId ZONE = ZoneId.of(ZONE_ID);

    private Times() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE);
    }

    public static OffsetDateTime toOffset(LocalDateTime local) {
        return local == null ? null : local.atZone(ZONE).toOffsetDateTime();
    }
}
