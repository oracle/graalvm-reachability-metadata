/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_code_gson.gson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Date and time handling: Gson's built-in `java.time` adapters, `setDateFormat` for
 * `java.util.Date`, and the `Calendar` adapter.
 *
 * A travel booking service stores itineraries as JSON documents. Flight segments carry zoned
 * departure and arrival times that cross a daylight-saving change, block times as `Duration`s,
 * and booking instants, while the itinerary itself holds local dates, times, periods, and
 * year-months. A legacy partner feed still exchanges `java.util.Date` and `Calendar` values, so
 * the service configures a date pattern for it and accepts ISO-8601 timestamps from partners.
 */
public class TravelItineraryDateTimeTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");
    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
    private static final Gson GSON = new GsonBuilder().serializeNulls().create();

    @Test
    void itineraryRoundTripsEveryTemporalValueExactly() {
        Itinerary itinerary = Itinerary.sample();

        String json = GSON.toJson(itinerary);
        Itinerary restored = GSON.fromJson(json, Itinerary.class);

        assertEquals(itinerary.reference, restored.reference);
        assertEquals(LocalDate.of(2025, 3, 29), restored.travelDate);
        assertEquals(LocalTime.of(5, 45), restored.checkInOpens);
        assertEquals(LocalDateTime.of(2025, 3, 30, 9, 15), restored.hotelPickup);
        assertEquals(itinerary.confirmedAt, restored.confirmedAt);
        assertEquals(OffsetTime.of(6, 0, 0, 0, ZoneOffset.ofHours(1)), restored.loungeOpens);
        assertEquals(Period.of(0, 6, 15), restored.visaValidity);
        assertEquals(YearMonth.of(2027, 11), restored.cardExpiry);
        assertEquals(Year.of(2025), restored.fiscalYear);
        assertEquals(MonthDay.of(2, 29), restored.memberSince);
        assertEquals(2, restored.segments.size());
        for (int i = 0; i < itinerary.segments.size(); i++) {
            FlightSegment expected = itinerary.segments.get(i);
            FlightSegment actual = restored.segments.get(i);
            assertEquals(expected.flightNumber, actual.flightNumber);
            assertEquals(expected.departure, actual.departure);
            assertEquals(expected.arrival, actual.arrival);
            assertEquals(expected.blockTime, actual.blockTime);
            assertEquals(expected.bookedAt, actual.bookedAt);
        }
        assertNull(restored.segments.get(1).bookedAt);
    }

    @Test
    void temporalValuesUseTheirStableComponentLayout() {
        JsonObject document = JsonParser.parseString(GSON.toJson(Itinerary.sample())).getAsJsonObject();

        assertEquals(JsonParser.parseString("""
                {"year":2025,"month":3,"day":29}"""), document.get("travelDate"));
        assertEquals(JsonParser.parseString("""
                {"hour":5,"minute":45,"second":0,"nano":0}"""), document.get("checkInOpens"));
        assertEquals(JsonParser.parseString("""
                {"years":0,"months":6,"days":15}"""), document.get("visaValidity"));
        assertEquals(JsonParser.parseString("""
                {"year":2027,"month":11}"""), document.get("cardExpiry"));
        assertEquals(JsonParser.parseString("""
                {"month":2,"day":29}"""), document.get("memberSince"));

        JsonObject firstSegment = document.getAsJsonArray("segments").get(0).getAsJsonObject();
        assertEquals(JsonParser.parseString("""
                {"seconds":1743289200,"nanos":250000000}"""), firstSegment.get("bookedAt"));
        assertEquals(JsonParser.parseString("""
                {"seconds":6300,"nanos":0}"""), firstSegment.get("blockTime"));
        JsonObject departure = firstSegment.getAsJsonObject("departure");
        assertEquals(3600, departure.getAsJsonObject("offset").get("totalSeconds").getAsInt());
        assertEquals("Europe/Berlin", departure.getAsJsonObject("zone").get("id").getAsString());
        assertEquals(JsonParser.parseString("""
                {"hour":1,"minute":30,"second":0,"nano":0}"""),
                departure.getAsJsonObject("dateTime").get("time"));

        assertTrue(document.getAsJsonArray("segments").get(1).getAsJsonObject().get("bookedAt")
                .isJsonNull());
    }

    @Test
    void zonedTimesKeepTheirRegionAcrossDaylightSavingChanges() {
        FlightSegment overnight = Itinerary.sample().segments.get(0);

        FlightSegment restored = GSON.fromJson(GSON.toJson(overnight), FlightSegment.class);

        assertEquals(BERLIN, restored.departure.getZone());
        assertEquals(ZoneOffset.ofHours(1), restored.departure.getOffset());
        assertEquals(ZoneOffset.ofHours(2), restored.arrival.getOffset());
        assertEquals(LocalTime.of(4, 15), restored.arrival.toLocalTime());
        assertEquals(restored.blockTime, Duration.between(restored.departure, restored.arrival));
        ZonedDateTime oneDayLater = restored.departure.plusDays(1);
        assertEquals(ZoneOffset.ofHours(2), oneDayLater.getOffset());
        assertEquals(LocalTime.of(1, 30), oneDayLater.toLocalTime());
    }

    @Test
    void scheduleKeyedByFlightBindsGenericCollectionsOfTemporalValues() {
        Map<String, List<ZonedDateTime>> schedule = Map.of(
                "LH400", List.of(
                        ZonedDateTime.of(2025, 11, 1, 10, 0, 0, 0, BERLIN),
                        ZonedDateTime.of(2025, 11, 2, 10, 0, 0, 0, BERLIN)),
                "LH401", List.of(ZonedDateTime.of(2025, 11, 1, 22, 0, 0, 0, NEW_YORK)));
        TypeToken<Map<String, List<ZonedDateTime>>> type = new TypeToken<>() { };

        Map<String, List<ZonedDateTime>> restored = GSON.fromJson(GSON.toJson(schedule, type.getType()), type);

        assertEquals(schedule, restored);
        assertEquals(NEW_YORK, restored.get("LH401").get(0).getZone());
        assertEquals(ZoneOffset.ofHours(-4), restored.get("LH401").get(0).getOffset());
    }

    @Test
    void malformedTemporalDocumentsAreRejected() {
        assertThrows(DateTimeException.class, () -> GSON.fromJson("""
                {"year":2025,"month":2,"day":30}""", LocalDate.class));
        assertThrows(JsonSyntaxException.class, () -> GSON.fromJson("""
                {"date":{"year":2025,"month":2,"day":28}}""", LocalDateTime.class));
        assertThrows(JsonSyntaxException.class, () -> GSON.fromJson("\"2025-02-28\"", LocalDate.class));
    }

    @Test
    void legacyPartnerFeedUsesTheConfiguredDatePatternAndAcceptsIsoTimestamps() {
        Gson partnerGson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                .create();
        Date issuedAt = new Date(1743298200250L);
        PartnerTicket ticket = new PartnerTicket("TK-1", issuedAt,
                new GregorianCalendar(2024, Calendar.MARCH, 5, 14, 30, 15));

        JsonObject document = partnerGson.toJsonTree(ticket).getAsJsonObject();
        String issuedText = document.get("issuedAt").getAsString();

        assertTrue(issuedText.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.250([+-]\\d{2}:\\d{2}|Z)"),
                issuedText);
        assertEquals(JsonParser.parseString("""
                {"year":2024,"month":2,"dayOfMonth":5,"hourOfDay":14,"minute":30,"second":15}"""),
                document.get("travelStart"));

        PartnerTicket restored = partnerGson.fromJson(document, PartnerTicket.class);
        assertEquals(issuedAt, restored.issuedAt);
        assertInstanceOf(GregorianCalendar.class, restored.travelStart);
        assertEquals(2024, restored.travelStart.get(Calendar.YEAR));
        assertEquals(Calendar.MARCH, restored.travelStart.get(Calendar.MONTH));
        assertEquals(5, restored.travelStart.get(Calendar.DAY_OF_MONTH));
        assertEquals(14, restored.travelStart.get(Calendar.HOUR_OF_DAY));
        assertEquals(30, restored.travelStart.get(Calendar.MINUTE));
        assertEquals(15, restored.travelStart.get(Calendar.SECOND));

        PartnerTicket fromPartner = partnerGson.fromJson("""
                {"ticketId":"TK-2","issuedAt":"2025-03-30T01:30:00Z","travelStart":null}""",
                PartnerTicket.class);
        assertEquals(Instant.parse("2025-03-30T01:30:00Z"), fromPartner.issuedAt.toInstant());
        assertNull(fromPartner.travelStart);
        assertThrows(JsonSyntaxException.class, () -> partnerGson.fromJson("""
                {"ticketId":"TK-3","issuedAt":"next tuesday"}""", PartnerTicket.class));
    }

    static final class Itinerary {
        String reference;
        LocalDate travelDate;
        LocalTime checkInOpens;
        LocalDateTime hotelPickup;
        OffsetDateTime confirmedAt;
        OffsetTime loungeOpens;
        Period visaValidity;
        YearMonth cardExpiry;
        Year fiscalYear;
        MonthDay memberSince;
        List<FlightSegment> segments;

        static Itinerary sample() {
            Itinerary itinerary = new Itinerary();
            itinerary.reference = "ZX7Q4P";
            itinerary.travelDate = LocalDate.of(2025, 3, 29);
            itinerary.checkInOpens = LocalTime.of(5, 45);
            itinerary.hotelPickup = LocalDateTime.of(2025, 3, 30, 9, 15);
            itinerary.confirmedAt = OffsetDateTime.of(2025, 3, 29, 23, 0, 0, 250_000_000,
                    ZoneOffset.ofHoursMinutes(5, 30));
            itinerary.loungeOpens = OffsetTime.of(6, 0, 0, 0, ZoneOffset.ofHours(1));
            itinerary.visaValidity = Period.of(0, 6, 15);
            itinerary.cardExpiry = YearMonth.of(2027, 11);
            itinerary.fiscalYear = Year.of(2025);
            itinerary.memberSince = MonthDay.of(2, 29);

            ZonedDateTime departure = ZonedDateTime.of(2025, 3, 30, 1, 30, 0, 0, BERLIN);
            Duration overnightBlock = Duration.ofMinutes(105);
            FlightSegment overnight = new FlightSegment("LH2040", departure,
                    departure.plus(overnightBlock), overnightBlock,
                    Instant.ofEpochSecond(1743289200L, 250_000_000));
            ZonedDateTime transatlantic = ZonedDateTime.of(2025, 3, 30, 10, 5, 0, 0, BERLIN);
            Duration transatlanticBlock = Duration.ofHours(8).plusMinutes(50);
            FlightSegment connection = new FlightSegment("LH400", transatlantic,
                    transatlantic.plus(transatlanticBlock).withZoneSameInstant(NEW_YORK),
                    transatlanticBlock, null);
            itinerary.segments = List.of(overnight, connection);
            return itinerary;
        }
    }

    static final class FlightSegment {
        String flightNumber;
        ZonedDateTime departure;
        ZonedDateTime arrival;
        Duration blockTime;
        Instant bookedAt;

        FlightSegment(String flightNumber, ZonedDateTime departure, ZonedDateTime arrival,
                Duration blockTime, Instant bookedAt) {
            this.flightNumber = flightNumber;
            this.departure = departure;
            this.arrival = arrival;
            this.blockTime = blockTime;
            this.bookedAt = bookedAt;
        }
    }

    static final class PartnerTicket {
        String ticketId;
        Date issuedAt;
        Calendar travelStart;

        PartnerTicket(String ticketId, Date issuedAt, Calendar travelStart) {
            this.ticketId = ticketId;
            this.issuedAt = issuedAt;
            this.travelStart = travelStart;
        }
    }
}
