/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_datatype.jackson_datatype_jsr310;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonFormat;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.jsr310.JavaTimeFeature;
import tools.jackson.datatype.jsr310.JavaTimeModule;

public class Jackson_datatype_jsr310Test {
    @Test
    void moduleExposesNameAndVersion() {
        JavaTimeModule module = new JavaTimeModule();

        assertThat(module.getModuleName()).isEqualTo(JavaTimeModule.class.getName());
        assertThat(module.version()).isNotNull();
    }

    @Test
    void serializesAndDeserializesIsoStringsForSupportedJavaTimeValues() throws Exception {
        ObjectMapper mapper = isoMapper();
        DateTimeValues values = DateTimeValues.sample();

        String json = mapper.writeValueAsString(values);

        assertThat(json)
                .contains("\"localDate\":\"2020-02-29\"")
                .contains("\"localTime\":\"13:45:59.123456789\"")
                .contains("\"localDateTime\":\"2020-02-29T13:45:59.123456789\"")
                .contains("\"offsetTime\":\"13:45:59.123456789+05:30\"")
                .contains("\"offsetDateTime\":\"2020-02-29T13:45:59.123456789+05:30\"")
                .contains("\"zonedDateTime\":\"2020-02-29T09:15:30.123456789+01:00[Europe\\/Paris]\"")
                .contains("\"instant\":\"2020-02-29T08:15:30.123456789Z\"")
                .contains("\"year\":2020")
                .contains("\"yearMonth\":\"2020-02\"")
                .contains("\"monthDay\":\"--02-29\"")
                .contains("\"period\":\"P1Y2M3D\"")
                .contains("\"duration\":\"PT1H2M3.000000004S\"")
                .contains("\"zoneId\":\"Europe\\/Paris\"")
                .contains("\"zoneOffset\":\"+05:30\"");

        DateTimeValues restored = mapper.readValue(json, DateTimeValues.class);

        assertDateTimeValues(restored, values);
    }

    @Test
    void honorsContextualJsonFormatPatternsOnJavaTimeProperties() throws Exception {
        ObjectMapper mapper = isoMapper();
        FormattedValues values = new FormattedValues(
                LocalDateTime.of(2022, 10, 5, 14, 30, 15),
                LocalDate.of(2022, 10, 5),
                LocalTime.of(14, 30, 15),
                YearMonth.of(2022, 10));

        String json = mapper.writeValueAsString(values);

        assertThat(json)
                .contains("\"meetingTime\":\"2022\\/10\\/05 14:30:15\"")
                .contains("\"compactDate\":\"20221005\"")
                .contains("\"clockTime\":\"14*30*15\"")
                .contains("\"billingMonth\":\"2022-10\"");

        FormattedValues restored = mapper.readValue(json, FormattedValues.class);

        assertThat(restored.meetingTime).isEqualTo(values.meetingTime);
        assertThat(restored.compactDate).isEqualTo(values.compactDate);
        assertThat(restored.clockTime).isEqualTo(values.clockTime);
        assertThat(restored.billingMonth).isEqualTo(values.billingMonth);
    }

    @Test
    void supportsTemporalValuesAsMapKeysAndValues() throws Exception {
        ObjectMapper mapper = isoMapper();
        Map<LocalDate, OffsetDateTime> deadlines = new LinkedHashMap<>();
        deadlines.put(LocalDate.of(2024, 1, 31), OffsetDateTime.parse("2024-01-31T23:45:07+01:00"));
        deadlines.put(LocalDate.of(2024, 2, 29), OffsetDateTime.parse("2024-02-29T10:15:30-05:00"));

        String json = mapper.writeValueAsString(deadlines);

        assertThat(json)
                .contains("\"2024-01-31\":\"2024-01-31T23:45:07+01:00\"")
                .contains("\"2024-02-29\":\"2024-02-29T10:15:30-05:00\"");

        JavaType type = mapper.getTypeFactory()
                .constructMapType(LinkedHashMap.class, LocalDate.class, OffsetDateTime.class);
        Map<LocalDate, OffsetDateTime> restored = mapper.readValue(json, type);

        assertThat(restored).containsExactlyEntriesOf(deadlines);
    }

    @Test
    void readsAllRegisteredJavaTimeMapKeyTypes() throws Exception {
        ObjectMapper mapper = isoMapper();

        assertSingleKey(mapper, "{\"2024-05-06\":\"date\"}", LocalDate.class, LocalDate.of(2024, 5, 6));
        assertSingleKey(mapper, "{\"07:08:09.000000010\":\"time\"}", LocalTime.class,
                LocalTime.of(7, 8, 9, 10));
        assertSingleKey(mapper, "{\"2024-05-06T07:08:09.000000010\":\"dateTime\"}", LocalDateTime.class,
                LocalDateTime.of(2024, 5, 6, 7, 8, 9, 10));
        assertSingleKey(mapper, "{\"07:08:09.000000010+02:00\":\"offsetTime\"}", OffsetTime.class,
                OffsetTime.of(7, 8, 9, 10, ZoneOffset.ofHours(2)));
        assertSingleKey(mapper, "{\"2024-05-06T07:08:09.000000010+02:00\":\"offsetDateTime\"}",
                OffsetDateTime.class, OffsetDateTime.of(2024, 5, 6, 7, 8, 9, 10, ZoneOffset.ofHours(2)));
        assertSingleKey(mapper, "{\"2024-05-06T07:08:09.000000010+02:00[Europe/Paris]\":\"zoned\"}",
                ZonedDateTime.class, ZonedDateTime.of(2024, 5, 6, 7, 8, 9, 10, ZoneId.of("Europe/Paris")));
        assertSingleKey(mapper, "{\"2024-05-06T05:08:09.000000010Z\":\"instant\"}", Instant.class,
                Instant.parse("2024-05-06T05:08:09.000000010Z"));
        assertSingleKey(mapper, "{\"2024\":\"year\"}", Year.class, Year.of(2024));
        assertSingleKey(mapper, "{\"2024-05\":\"yearMonth\"}", YearMonth.class, YearMonth.of(2024, 5));
        assertSingleKey(mapper, "{\"--05-06\":\"monthDay\"}", MonthDay.class, MonthDay.of(5, 6));
        assertSingleKey(mapper, "{\"P1Y2M3D\":\"period\"}", Period.class, Period.of(1, 2, 3));
        assertSingleKey(mapper, "{\"PT4H5M6S\":\"duration\"}", Duration.class,
                Duration.ofHours(4).plusMinutes(5).plusSeconds(6));
        assertSingleKey(mapper, "{\"Europe/Paris\":\"zoneId\"}", ZoneId.class, ZoneId.of("Europe/Paris"));
        assertSingleKey(mapper, "{\"+02:00\":\"zoneOffset\"}", ZoneOffset.class, ZoneOffset.ofHours(2));
    }

    @Test
    void supportsObjectMapperModuleAutoDiscovery() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .findAndAddModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        MonthDay leapDay = MonthDay.of(2, 29);

        String json = mapper.writeValueAsString(leapDay);

        assertThat(json).isEqualTo("\"--02-29\"");
        assertThat(mapper.readValue(json, MonthDay.class)).isEqualTo(leapDay);
    }

    @Test
    void supportsArrayAndNumericTimestampRepresentations() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        LocalDate date = LocalDate.of(2020, 2, 29);
        LocalTime time = LocalTime.of(1, 2, 3, 4);
        LocalDateTime dateTime = LocalDateTime.of(date, time);
        Instant instant = Instant.parse("2022-01-01T00:00:00.123456789Z");

        String dateJson = mapper.writeValueAsString(date);
        String timeJson = mapper.writeValueAsString(time);
        String dateTimeJson = mapper.writeValueAsString(dateTime);
        String instantJson = mapper.writeValueAsString(instant);

        assertThat(dateJson).isEqualTo("[2020,2,29]");
        assertThat(timeJson).startsWith("[1,2,3");
        assertThat(dateTimeJson).startsWith("[2020,2,29,1,2,3");
        assertThat(instantJson).doesNotStartWith("\"");
        assertThat(mapper.readValue(dateJson, LocalDate.class)).isEqualTo(date);
        assertThat(mapper.readValue(timeJson, LocalTime.class)).isEqualTo(time);
        assertThat(mapper.readValue(dateTimeJson, LocalDateTime.class)).isEqualTo(dateTime);
        assertThat(mapper.readValue(instantJson, Instant.class)).isEqualTo(instant);
    }

    @Test
    void supportsMillisecondTimestampConfigurationForInstantsAndDurations() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();
        Instant instant = Instant.ofEpochMilli(1_640_995_200_123L);
        Duration duration = Duration.ofMillis(3_723_004L);

        String instantJson = mapper.writeValueAsString(instant);
        String durationJson = mapper.writeValueAsString(duration);

        assertThat(instantJson).isEqualTo("1640995200123");
        assertThat(durationJson).isEqualTo("\"PT1H2M3.004S\"");
        assertThat(mapper.readValue(instantJson, Instant.class)).isEqualTo(instant);
        assertThat(mapper.readValue(durationJson, Duration.class)).isEqualTo(duration);
    }

    @Test
    void honorsDurationJsonFormatUnitPatterns() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .build();
        DurationUnits values = new DurationUnits(Duration.ofMinutes(90), Duration.ofHours(5));

        String json = mapper.writeValueAsString(values);

        assertThat(json).contains("\"minutes\":90").contains("\"hours\":5");

        DurationUnits restored = mapper.readValue("{\"minutes\":45,\"hours\":3}", DurationUnits.class);

        assertThat(restored.minutes).isEqualTo(Duration.ofMinutes(45));
        assertThat(restored.hours).isEqualTo(Duration.ofHours(3));
    }

    @Test
    void supportsOneBasedMonthFeatureForMonthEnumValues() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule().enable(JavaTimeFeature.ONE_BASED_MONTHS))
                .enable(SerializationFeature.WRITE_ENUMS_USING_INDEX)
                .build();

        assertThat(mapper.writeValueAsString(Month.JULY)).isEqualTo("7");
        assertThat(mapper.readValue("7", Month.class)).isEqualTo(Month.JULY);
    }

    @Test
    void usesMapperTimeZoneForLenientDateAndDateTimeParsing() throws Exception {
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule().enable(JavaTimeFeature.USE_TIME_ZONE_FOR_LENIENT_DATE_PARSING))
                .defaultTimeZone(TimeZone.getTimeZone("GMT-05:00"))
                .build();

        LocalDate date = mapper.readValue("\"2024-01-01T02:00:00Z\"", LocalDate.class);
        LocalDateTime dateTime = mapper.readValue("\"2024-01-15T12:00:00Z\"", LocalDateTime.class);

        assertThat(date).isEqualTo(LocalDate.of(2023, 12, 31));
        assertThat(dateTime).isEqualTo(LocalDateTime.of(2024, 1, 15, 7, 0));
    }

    @Test
    void preservesExplicitZoneIdWhenSerializingZonedDateTimeMapKeys() throws Exception {
        ObjectMapper mapper = isoMapper();
        ZonedDateTime departure = ZonedDateTime.of(
                LocalDateTime.of(2024, 3, 31, 1, 30), ZoneId.of("Europe/Paris"));
        Map<ZonedDateTime, String> departures = new LinkedHashMap<>();
        departures.put(departure, "spring-changeover");

        String json = mapper.writeValueAsString(departures);

        assertThat(json).contains("\"2024-03-31T01:30:00+01:00[Europe\\/Paris]\":\"spring-changeover\"");
        Map<ZonedDateTime, String> restored = readMapWithKeys(mapper, json, ZonedDateTime.class);
        assertThat(restored).containsEntry(departure, "spring-changeover");
    }

    private static ObjectMapper isoMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule().disable(JavaTimeFeature.NORMALIZE_DESERIALIZED_ZONE_ID))
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID)
                .build();
    }

    private static <K> void assertSingleKey(ObjectMapper mapper, String json, Class<K> keyType, K expectedKey)
            throws Exception {
        Map<K, String> restored = readMapWithKeys(mapper, json, keyType);

        assertThat(restored).containsKey(expectedKey);
    }

    private static <K> Map<K, String> readMapWithKeys(ObjectMapper mapper, String json, Class<K> keyType)
            throws Exception {
        JavaType mapType = mapper.getTypeFactory().constructMapType(LinkedHashMap.class, keyType, String.class);

        return mapper.readValue(json, mapType);
    }

    private static void assertDateTimeValues(DateTimeValues actual, DateTimeValues expected) {
        assertThat(actual.localDate).isEqualTo(expected.localDate);
        assertThat(actual.localTime).isEqualTo(expected.localTime);
        assertThat(actual.localDateTime).isEqualTo(expected.localDateTime);
        assertThat(actual.offsetTime).isEqualTo(expected.offsetTime);
        assertThat(actual.offsetDateTime).isEqualTo(expected.offsetDateTime);
        assertThat(actual.zonedDateTime).isEqualTo(expected.zonedDateTime);
        assertThat(actual.instant).isEqualTo(expected.instant);
        assertThat(actual.year).isEqualTo(expected.year);
        assertThat(actual.yearMonth).isEqualTo(expected.yearMonth);
        assertThat(actual.monthDay).isEqualTo(expected.monthDay);
        assertThat(actual.period).isEqualTo(expected.period);
        assertThat(actual.duration).isEqualTo(expected.duration);
        assertThat(actual.zoneId).isEqualTo(expected.zoneId);
        assertThat(actual.zoneOffset).isEqualTo(expected.zoneOffset);
    }

    public static final class DateTimeValues {
        public LocalDate localDate;
        public LocalTime localTime;
        public LocalDateTime localDateTime;
        public OffsetTime offsetTime;
        public OffsetDateTime offsetDateTime;
        public ZonedDateTime zonedDateTime;
        public Instant instant;
        public Year year;
        public YearMonth yearMonth;
        public MonthDay monthDay;
        public Period period;
        public Duration duration;
        public ZoneId zoneId;
        public ZoneOffset zoneOffset;

        public DateTimeValues() {
        }

        static DateTimeValues sample() {
            DateTimeValues values = new DateTimeValues();
            values.localDate = LocalDate.of(2020, 2, 29);
            values.localTime = LocalTime.of(13, 45, 59, 123_456_789);
            values.localDateTime = LocalDateTime.of(values.localDate, values.localTime);
            values.offsetTime = OffsetTime.of(values.localTime, ZoneOffset.ofHoursMinutes(5, 30));
            values.offsetDateTime = OffsetDateTime.of(values.localDateTime, ZoneOffset.ofHoursMinutes(5, 30));
            values.zonedDateTime = ZonedDateTime.of(
                    LocalDateTime.of(2020, 2, 29, 9, 15, 30, 123_456_789), ZoneId.of("Europe/Paris"));
            values.instant = Instant.parse("2020-02-29T08:15:30.123456789Z");
            values.year = Year.of(2020);
            values.yearMonth = YearMonth.of(2020, 2);
            values.monthDay = MonthDay.of(2, 29);
            values.period = Period.of(1, 2, 3);
            values.duration = Duration.ofHours(1).plusMinutes(2).plusSeconds(3).plusNanos(4);
            values.zoneId = ZoneId.of("Europe/Paris");
            values.zoneOffset = ZoneOffset.ofHoursMinutes(5, 30);
            return values;
        }
    }

    public static final class FormattedValues {
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu/MM/dd HH:mm:ss")
        public LocalDateTime meetingTime;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuuMMdd")
        public LocalDate compactDate;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "HH*mm*ss")
        public LocalTime clockTime;

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM")
        public YearMonth billingMonth;

        public FormattedValues() {
        }

        FormattedValues(LocalDateTime meetingTime, LocalDate compactDate, LocalTime clockTime, YearMonth billingMonth) {
            this.meetingTime = meetingTime;
            this.compactDate = compactDate;
            this.clockTime = clockTime;
            this.billingMonth = billingMonth;
        }
    }

    public static final class DurationUnits {
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT, pattern = "MINUTES")
        public Duration minutes;

        @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT, pattern = "HOURS")
        public Duration hours;

        public DurationUnits() {
        }

        DurationUnits(Duration minutes, Duration hours) {
            this.minutes = minutes;
            this.hours = hours;
        }
    }
}
