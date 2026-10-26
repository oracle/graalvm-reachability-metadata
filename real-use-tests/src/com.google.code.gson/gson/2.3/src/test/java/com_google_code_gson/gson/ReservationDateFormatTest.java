/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/// Exercises Gson's date-format configuration subsystem to import and reschedule offset-bearing reservations.
public class ReservationDateFormatTest {

    @Test
    @Timeout(60)
    public void reschedulesAReservationAcrossAnOffsetChangeWithoutLosingTheInstantOrMilliseconds() {
        Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                .create();
        Reservation reservation = gson.fromJson("""
                {
                  "reservationId": "R-402",
                  "startsAt": "2025-03-30T01:30:00.125+01:00",
                  "endsAt": "2025-03-30T03:30:00.125+02:00"
                }
                """, Reservation.class);

        assertEquals("R-402", reservation.reservationId);
        assertEquals(Instant.parse("2025-03-30T00:30:00.125Z"), reservation.startsAt.toInstant());
        assertEquals(Instant.parse("2025-03-30T01:30:00.125Z"), reservation.endsAt.toInstant());
        // The local clock advances two hours, but the reservation lasts only one elapsed hour.
        Duration originalDuration = Duration.between(reservation.startsAt.toInstant(), reservation.endsAt.toInstant());
        assertEquals(Duration.ofHours(1), originalDuration);

        Duration delay = Duration.ofHours(2);
        reservation.startsAt = Date.from(reservation.startsAt.toInstant().plus(delay));
        reservation.endsAt = Date.from(reservation.endsAt.toInstant().plus(delay));
        String confirmation = gson.toJson(reservation);
        JsonObject published = new JsonParser().parse(confirmation).getAsJsonObject();

        assertEquals(3, published.entrySet().size());
        assertEquals("R-402", published.get("reservationId").getAsString());
        assertTrue(published.getAsJsonPrimitive("startsAt").isString());
        assertTrue(published.getAsJsonPrimitive("endsAt").isString());
        // A client resolves the emitted offset, independent of the server's default time zone.
        Instant publishedStart = OffsetDateTime.parse(published.get("startsAt").getAsString()).toInstant();
        Instant publishedEnd = OffsetDateTime.parse(published.get("endsAt").getAsString()).toInstant();
        assertEquals(Instant.parse("2025-03-30T02:30:00.125Z"), publishedStart);
        assertEquals(Instant.parse("2025-03-30T03:30:00.125Z"), publishedEnd);
        assertEquals(originalDuration, Duration.between(publishedStart, publishedEnd));

        Reservation restored = gson.fromJson(confirmation, Reservation.class);
        assertEquals(reservation.reservationId, restored.reservationId);
        assertEquals(reservation.startsAt, restored.startsAt);
        assertEquals(reservation.endsAt, restored.endsAt);
    }

    public static class Reservation {
        private String reservationId;
        private Date startsAt;
        private Date endsAt;
    }
}
