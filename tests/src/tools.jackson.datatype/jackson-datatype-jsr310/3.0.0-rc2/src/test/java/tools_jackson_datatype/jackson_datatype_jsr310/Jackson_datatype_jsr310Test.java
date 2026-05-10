/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_datatype.jackson_datatype_jsr310;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Period;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.jsr310.JavaTimeFeature;
import tools.jackson.datatype.jsr310.JavaTimeModule;

public class Jackson_datatype_jsr310Test {
    private static final ZoneOffset PLUS_TWO = ZoneOffset.ofHours(2);

    private final ObjectMapper isoMapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
            .disable(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS)
            .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
            .build();

    @Test
    void serializesAndDeserializesIsoRepresentationsForJavaTimeValueTypes() throws Exception {
        TemporalSnapshot snapshot = new TemporalSnapshot(
                Instant.parse("2024-02-03T04:05:06.123456789Z"),
                OffsetDateTime.of(2024, 2, 3, 6, 5, 6, 123456789, PLUS_TWO),
                LocalDate.of(2024, 2, 3),
                LocalTime.of(4, 5, 6, 123456789),
                LocalDateTime.of(2024, 2, 3, 4, 5, 6, 123456789),
                OffsetTime.of(4, 5, 6, 123456789, PLUS_TWO),
                Period.of(1, 2, 3),
                Duration.ofHours(5).plusMinutes(6).plusSeconds(7).plusMillis(8),
                Year.of(2024),
                YearMonth.of(2024, 2),
                MonthDay.of(2, 3),
                ZoneId.of("Europe/Paris"),
                PLUS_TWO);

        String json = isoMapper.writeValueAsString(snapshot);
        JsonNode tree = isoMapper.readTree(json);

        assertThat(tree.get("instant").asText()).isEqualTo("2024-02-03T04:05:06.123456789Z");
        assertThat(tree.get("offsetDateTime").asText()).isEqualTo("2024-02-03T06:05:06.123456789+02:00");
        assertThat(tree.get("localDate").asText()).isEqualTo("2024-02-03");
        assertThat(tree.get("localTime").asText()).isEqualTo("04:05:06.123456789");
        assertThat(tree.get("localDateTime").asText()).isEqualTo("2024-02-03T04:05:06.123456789");
        assertThat(tree.get("offsetTime").asText()).isEqualTo("04:05:06.123456789+02:00");
        assertThat(tree.get("period").asText()).isEqualTo("P1Y2M3D");
        assertThat(tree.get("duration").asText()).isEqualTo("PT5H6M7.008S");
        assertThat(tree.get("year").asText()).isEqualTo("2024");
        assertThat(tree.get("yearMonth").asText()).isEqualTo("2024-02");
        assertThat(tree.get("monthDay").asText()).isEqualTo("--02-03");
        assertThat(tree.get("zoneId").asText()).isEqualTo("Europe/Paris");
        assertThat(tree.get("zoneOffset").asText()).isEqualTo("+02:00");

        assertThat(isoMapper.readValue(json, TemporalSnapshot.class)).isEqualTo(snapshot);
    }

    @Test
    void discoversJavaTimeModuleThroughMapperModuleDiscovery() throws Exception {
        ObjectMapper discoveredMapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build();
        AutoDiscoveredSnapshot snapshot = new AutoDiscoveredSnapshot(
                LocalDate.of(2024, 4, 5),
                OffsetDateTime.of(2024, 4, 5, 6, 7, 8, 0, ZoneOffset.UTC),
                Duration.ofMinutes(45));

        String json = discoveredMapper.writeValueAsString(snapshot);
        JsonNode tree = discoveredMapper.readTree(json);

        assertThat(tree.get("date").asText()).isEqualTo("2024-04-05");
        assertThat(tree.get("publishedAt").asText()).isEqualTo("2024-04-05T06:07:08Z");
        assertThat(tree.get("processingTime").asText()).isEqualTo("PT45M");
        assertThat(discoveredMapper.readValue(json, AutoDiscoveredSnapshot.class)).isEqualTo(snapshot);
    }

    @Test
    void writesAndReadsArrayAndNumericTimestampShapes() throws Exception {
        ObjectMapper timestampMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .enable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .enable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build();
        TimestampSnapshot snapshot = new TimestampSnapshot(
                LocalDate.of(2024, 2, 3),
                LocalTime.of(4, 5, 6, 123456789),
                LocalDateTime.of(2024, 2, 3, 4, 5, 6, 123456789),
                Instant.parse("2024-02-03T04:05:06.123456789Z"),
                Duration.ofHours(2).plusMinutes(3).plusSeconds(4).plusMillis(500));

        String json = timestampMapper.writeValueAsString(snapshot);
        JsonNode tree = timestampMapper.readTree(json);

        assertThat(intValues(tree.get("date"))).containsExactly(2024, 2, 3);
        assertThat(intValues(tree.get("time"))).containsExactly(4, 5, 6, 123456789);
        assertThat(intValues(tree.get("dateTime"))).containsExactly(2024, 2, 3, 4, 5, 6, 123456789);
        assertThat(json).contains("\"instant\":1706933106.123456789");
        assertThat(tree.get("duration").decimalValue()).isEqualByComparingTo(new BigDecimal("7384.500000000"));

        assertThat(timestampMapper.readValue(json, TimestampSnapshot.class)).isEqualTo(snapshot);
    }

    @Test
    void serializesAndDeserializesJavaTimeMapKeys() throws Exception {
        MapKeySnapshot snapshot = new MapKeySnapshot(
                linkedMap(LocalDate.of(2024, 2, 3), "release"),
                linkedMap(Instant.parse("2024-02-03T04:05:06Z"), "created"),
                linkedMap(YearMonth.of(2024, 2), "accounting"),
                linkedMap(ZoneOffset.ofHoursMinutes(5, 30), "india"),
                linkedMap(Duration.ofMinutes(90), "meeting"));

        String json = isoMapper.writeValueAsString(snapshot);
        JsonNode tree = isoMapper.readTree(json);

        assertThat(tree.get("dates").has("2024-02-03")).isTrue();
        assertThat(tree.get("instants").has("2024-02-03T04:05:06Z")).isTrue();
        assertThat(tree.get("accountingMonths").has("2024-02")).isTrue();
        assertThat(tree.get("offsets").has("+05:30")).isTrue();
        assertThat(tree.get("durations").has("PT1H30M")).isTrue();

        assertThat(isoMapper.readValue(json, MapKeySnapshot.class)).isEqualTo(snapshot);
    }

    @Test
    void preservesRegionIdForZonedDateTimeValuesAndKeysWhenConfigured() throws Exception {
        ObjectMapper zonedMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule().disable(JavaTimeFeature.NORMALIZE_DESERIALIZED_ZONE_ID))
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATE_KEYS_AS_TIMESTAMPS)
                .enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build();
        ZonedDateTime parisTime = ZonedDateTime.of(
                2024, 3, 31, 1, 30, 0, 0, ZoneId.of("Europe/Paris"));
        ZonedSnapshot snapshot = new ZonedSnapshot(parisTime, linkedMap(parisTime, "before-dst-change"));

        String json = zonedMapper.writeValueAsString(snapshot);
        JsonNode tree = zonedMapper.readTree(json);

        assertThat(tree.get("value").asText()).isEqualTo("2024-03-31T01:30:00+01:00[Europe/Paris]");
        assertThat(tree.get("valuesByTime").has("2024-03-31T01:30:00+01:00[Europe/Paris]")).isTrue();

        assertThat(zonedMapper.readValue(json, ZonedSnapshot.class)).isEqualTo(snapshot);
    }

    @Test
    void honorsPropertyLevelJsonFormatForTemporalPatternsAndUnits() throws Exception {
        FormattedSnapshot snapshot = new FormattedSnapshot(
                LocalDate.of(2024, 2, 3),
                LocalTime.of(4, 5, 6),
                YearMonth.of(2024, 2),
                LocalDate.ofEpochDay(20_000),
                Duration.ofMillis(1_500));

        String json = isoMapper.writeValueAsString(snapshot);
        JsonNode tree = isoMapper.readTree(json);

        assertThat(tree.get("date").asText()).isEqualTo("03.02.2024");
        assertThat(tree.get("time").asText()).isEqualTo("04:05:06");
        assertThat(tree.get("month").asText()).isEqualTo("2024/02");
        assertThat(tree.get("epochDay").asLong()).isEqualTo(20_000L);
        assertThat(tree.get("timeout").asLong()).isEqualTo(1_500L);

        assertThat(isoMapper.readValue(json, FormattedSnapshot.class)).isEqualTo(snapshot);
    }

    @Test
    void allowsStringifiedNumericTimestampsWithCustomOffsetDateTimeFormatterWhenConfigured() throws Exception {
        ObjectMapper stringifiedTimestampMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule().enable(JavaTimeFeature.ALWAYS_ALLOW_STRINGIFIED_DATE_TIMESTAMPS))
                .defaultTimeZone(TimeZone.getTimeZone("UTC"))
                .enable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build();

        StringifiedTimestampSnapshot snapshot = stringifiedTimestampMapper.readValue(
                "{\"timestamp\":\"1706933106.123456789\"}",
                StringifiedTimestampSnapshot.class);

        assertThat(snapshot).isEqualTo(new StringifiedTimestampSnapshot(
                OffsetDateTime.of(2024, 2, 3, 4, 5, 6, 123456789, ZoneOffset.UTC)));
    }

    @Test
    void appliesJavaTimeModuleFeatureFlagsForMonthsAndLenientLocalDates() throws Exception {
        ObjectMapper oneBasedMonthMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule().enable(JavaTimeFeature.ONE_BASED_MONTHS))
                .enable(SerializationFeature.WRITE_ENUMS_USING_INDEX)
                .build();

        String json = oneBasedMonthMapper.writeValueAsString(new MonthSnapshot(Month.MARCH));
        assertThat(oneBasedMonthMapper.readTree(json).get("month").asInt()).isEqualTo(3);
        assertThat(oneBasedMonthMapper.readValue(json, MonthSnapshot.class)).isEqualTo(new MonthSnapshot(Month.MARCH));
        assertThat(oneBasedMonthMapper.readValue("{\"month\":\"MARCH\"}", MonthSnapshot.class))
                .isEqualTo(new MonthSnapshot(Month.MARCH));

        ObjectMapper contextTimeZoneMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule().enable(JavaTimeFeature.USE_TIME_ZONE_FOR_LENIENT_DATE_PARSING))
                .defaultTimeZone(TimeZone.getTimeZone("America/New_York"))
                .build();
        ObjectMapper defaultLenientMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .defaultTimeZone(TimeZone.getTimeZone("America/New_York"))
                .build();

        String nearMidnightUtc = "\"2024-01-01T01:00:00Z\"";
        assertThat(defaultLenientMapper.readValue(nearMidnightUtc, LocalDate.class))
                .isEqualTo(LocalDate.of(2024, 1, 1));
        assertThat(contextTimeZoneMapper.readValue(nearMidnightUtc, LocalDate.class))
                .isEqualTo(LocalDate.of(2023, 12, 31));
    }

    private static List<Integer> intValues(JsonNode arrayNode) {
        List<Integer> values = new ArrayList<>();
        arrayNode.forEach(value -> values.add(value.asInt()));
        return values;
    }

    private static <K, V> Map<K, V> linkedMap(K key, V value) {
        Map<K, V> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    public record TemporalSnapshot(
            Instant instant,
            OffsetDateTime offsetDateTime,
            LocalDate localDate,
            LocalTime localTime,
            LocalDateTime localDateTime,
            OffsetTime offsetTime,
            Period period,
            Duration duration,
            Year year,
            YearMonth yearMonth,
            MonthDay monthDay,
            ZoneId zoneId,
            ZoneOffset zoneOffset) {
    }

    public record TimestampSnapshot(
            LocalDate date,
            LocalTime time,
            LocalDateTime dateTime,
            Instant instant,
            Duration duration) {
    }

    public record AutoDiscoveredSnapshot(
            LocalDate date,
            OffsetDateTime publishedAt,
            Duration processingTime) {
    }

    public record MapKeySnapshot(
            Map<LocalDate, String> dates,
            Map<Instant, String> instants,
            Map<YearMonth, String> accountingMonths,
            Map<ZoneOffset, String> offsets,
            Map<Duration, String> durations) {
    }

    public record ZonedSnapshot(ZonedDateTime value, Map<ZonedDateTime, String> valuesByTime) {
    }

    public record FormattedSnapshot(
            @JsonFormat(pattern = "dd.MM.uuuu") LocalDate date,
            @JsonFormat(pattern = "HH:mm:ss") LocalTime time,
            @JsonFormat(pattern = "uuuu/MM") YearMonth month,
            @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT) LocalDate epochDay,
            @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT, pattern = "MILLIS") Duration timeout) {
    }

    public record MonthSnapshot(Month month) {
    }

    public record StringifiedTimestampSnapshot(
            @JsonFormat(pattern = "uuuu/MM/dd HH:mm:ss XXX") OffsetDateTime timestamp) {
    }
}
