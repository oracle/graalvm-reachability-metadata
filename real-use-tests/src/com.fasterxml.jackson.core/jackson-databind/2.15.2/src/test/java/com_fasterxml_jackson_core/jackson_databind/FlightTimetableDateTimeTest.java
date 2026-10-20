/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;

/**
 * Date and time handling: `java.util.Date` and `Calendar` written as epoch timestamps or through
 * `StdDateFormat` in the mapper's default time zone, per-property `@JsonFormat` patterns and time
 * zones, and `java.time` values through `JavaTimeModule` with timestamp, duration and
 * context-time-zone features.
 *
 * An airline timetable service publishes flight legs to partner systems: legacy partners take
 * `Date` fields, newer ones take ISO-8601 `java.time` values, departure boards want local
 * wall-clock times in the airport's zone, and malformed times from a partner feed are rejected
 * with the offending field named.
 */
public class FlightTimetableDateTimeTest {

    /// 2023-11-14T22:13:20Z.
    private static final long DEPARTURE_MILLIS = 1_700_000_000_000L;

    @Test
    void writesLegacyDatesAsTimestampsByDefault() throws Exception {
        ObjectMapper mapper = JsonMapper.builder().build();
        LegacyLeg leg = new LegacyLeg("KL1234", new Date(DEPARTURE_MILLIS), new Date(DEPARTURE_MILLIS));

        JsonNode tree = mapper.readTree(mapper.writeValueAsString(leg));

        assertTrue(tree.get("departure").isNumber());
        assertEquals(DEPARTURE_MILLIS, tree.get("departure").asLong());
        assertEquals("2023-11-14 23:13", tree.get("boardLocalTime").asText());
    }

    @Test
    void writesLegacyDatesAsIsoTextInMapperTimeZone() throws Exception {
        ObjectMapper utc = JsonMapper.builder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        ObjectMapper newYork = JsonMapper.builder()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .defaultTimeZone(TimeZone.getTimeZone("America/New_York"))
                .build();
        LegacyLeg leg = new LegacyLeg("KL1234", new Date(DEPARTURE_MILLIS), new Date(DEPARTURE_MILLIS));

        assertEquals("""
                {"flight":"KL1234","departure":"2023-11-14T22:13:20.000+00:00",\
                "boardLocalTime":"2023-11-14 23:13"}""", utc.writeValueAsString(leg));
        assertEquals("""
                {"flight":"KL1234","departure":"2023-11-14T17:13:20.000-05:00",\
                "boardLocalTime":"2023-11-14 23:13"}""", newYork.writeValueAsString(leg));
    }

    @Test
    void readsLegacyDatesFromIsoTextEpochMillisAndPatterns() throws Exception {
        ObjectMapper mapper = JsonMapper.builder().build();

        LegacyLeg fromIso = mapper.readValue("""
                {"flight": "AF7", "departure": "2023-11-14T23:13:20+01:00", "boardLocalTime": "2023-11-14 23:13"}
                """, LegacyLeg.class);
        LegacyLeg fromMillis = mapper.readValue("""
                {"flight": "AF7", "departure": 1700000000000, "boardLocalTime": "2023-11-15 08:30"}
                """, LegacyLeg.class);
        Manifest manifest = mapper.readValue("""
                {"flight": "AF7", "closedAt": "2023-11-14T21:43:20.000Z"}
                """, Manifest.class);

        assertEquals(DEPARTURE_MILLIS, fromIso.departure().getTime());
        assertEquals(DEPARTURE_MILLIS - 20_000L, fromIso.boardLocalTime().getTime());
        assertEquals(DEPARTURE_MILLIS, fromMillis.departure().getTime());
        assertEquals(Instant.parse("2023-11-15T07:30:00Z"), fromMillis.boardLocalTime().toInstant());
        assertEquals(Instant.parse("2023-11-14T21:43:20Z"), manifest.closedAt().toInstant());
        assertEquals(TimeZone.getTimeZone("UTC").getRawOffset(), manifest.closedAt().getTimeZone().getRawOffset());
    }

    @Test
    void writesJavaTimeValuesAsIsoTextWithModule() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .build();
        Leg leg = new Leg("LH400", LocalDate.of(2023, 11, 14), Instant.ofEpochMilli(DEPARTURE_MILLIS),
                OffsetDateTime.of(2023, 11, 15, 1, 5, 0, 0, ZoneOffset.ofHours(-5)),
                Duration.ofMinutes(470), LocalDateTime.of(2023, 11, 14, 22, 50));

        String json = mapper.writeValueAsString(leg);

        assertEquals("""
                {"flight":"LH400","serviceDay":"2023-11-14","departure":"2023-11-14T22:13:20Z",\
                "arrival":"2023-11-15T01:05:00-05:00","blockTime":"PT7H50M",\
                "gateClosesAt":"14.11.2023 22:50"}""", json);
    }

    @Test
    void writesJavaTimeValuesAsTimestampsWhenEnabled() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        Leg leg = new Leg("LH400", LocalDate.of(2023, 11, 14), Instant.ofEpochMilli(DEPARTURE_MILLIS),
                OffsetDateTime.of(2023, 11, 15, 1, 5, 0, 0, ZoneOffset.ofHours(-5)),
                Duration.ofMinutes(470), LocalDateTime.of(2023, 11, 14, 22, 50));

        JsonNode tree = mapper.valueToTree(leg);

        assertEquals("[2023,11,14]", tree.get("serviceDay").toString());
        assertEquals(1_700_000_000L, tree.get("departure").decimalValue().longValueExact());
        assertEquals(28_200L, tree.get("blockTime").decimalValue().longValueExact());
        assertEquals("14.11.2023 22:50", tree.get("gateClosesAt").asText());
    }

    @Test
    void readsOffsetTimesAdjustedToContextZoneOrKeptAsSent() throws Exception {
        String json = """
                {"flight": "LH400", "serviceDay": "2023-11-14", "departure": "2023-11-14T22:13:20Z",
                 "arrival": "2023-11-15T01:05:00-05:00", "blockTime": "PT7H50M",
                 "gateClosesAt": "14.11.2023 22:50"}
                """;
        ObjectMapper adjusting = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        ObjectMapper preserving = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build();

        Leg adjusted = adjusting.readValue(json, Leg.class);
        Leg preserved = preserving.readValue(json, Leg.class);

        assertEquals(LocalDate.of(2023, 11, 14), adjusted.serviceDay());
        assertEquals(Instant.ofEpochMilli(DEPARTURE_MILLIS), adjusted.departure());
        assertEquals(Duration.ofHours(7).plusMinutes(50), adjusted.blockTime());
        assertEquals(LocalDateTime.of(2023, 11, 14, 22, 50), adjusted.gateClosesAt());
        assertEquals(ZoneOffset.UTC, adjusted.arrival().getOffset());
        assertEquals(6, adjusted.arrival().getHour());
        assertEquals(ZoneOffset.ofHours(-5), preserved.arrival().getOffset());
        assertEquals(1, preserved.arrival().getHour());
        assertTrue(adjusted.arrival().isEqual(preserved.arrival()));
    }

    @Test
    void rejectsMalformedPartnerTimesWithFieldPath() {
        ObjectMapper mapper = JsonMapper.builder().build();

        InvalidFormatException error = assertThrows(InvalidFormatException.class,
                () -> mapper.readValue("""
                        {"flight": "BA117", "departure": "14/11/2023 22:13", "boardLocalTime": "2023-11-14 23:13"}
                        """, LegacyLeg.class));

        assertEquals("14/11/2023 22:13", error.getValue());
        assertEquals(Date.class, error.getTargetType());
        assertEquals("departure", error.getPath().get(0).getFieldName());
    }

    @Test
    void refusesJavaTimeValuesWithoutTheModule() {
        ObjectMapper mapper = JsonMapper.builder().build();
        Leg leg = new Leg("LH400", LocalDate.of(2023, 11, 14), Instant.ofEpochMilli(DEPARTURE_MILLIS),
                OffsetDateTime.of(2023, 11, 15, 1, 5, 0, 0, ZoneOffset.ofHours(-5)),
                Duration.ofMinutes(470), LocalDateTime.of(2023, 11, 14, 22, 50));

        InvalidDefinitionException error = assertThrows(InvalidDefinitionException.class,
                () -> mapper.writeValueAsString(leg));

        assertTrue(error.getOriginalMessage().contains("Java 8 date/time type"));
        assertTrue(error.getOriginalMessage().contains("jackson-datatype-jsr310"));
    }

    /// A leg as legacy partners exchange it, with the boarding time shown in Paris wall-clock time.
    @JsonPropertyOrder({"flight", "departure", "boardLocalTime"})
    public record LegacyLeg(
            String flight,
            Date departure,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm", timezone = "Europe/Paris")
            Date boardLocalTime) {
    }

    /// The passenger manifest, closed at a calendar instant.
    public record Manifest(String flight, Calendar closedAt) {
    }

    /// A leg as newer partners exchange it, with `java.time` values.
    @JsonPropertyOrder({"flight", "serviceDay", "departure", "arrival", "blockTime", "gateClosesAt"})
    public record Leg(
            String flight,
            LocalDate serviceDay,
            Instant departure,
            OffsetDateTime arrival,
            Duration blockTime,
            @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "dd.MM.yyyy HH:mm")
            LocalDateTime gateClosesAt) {
    }
}
