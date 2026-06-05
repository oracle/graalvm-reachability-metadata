/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_datatype.jackson_datatype_jsr310;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class Jackson_datatype_jsr310Test {
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
                .contains("\"instant\":\"2020-02-29T08:15:30.123456789Z\"")
                .contains("\"yearMonth\":\"2020-02\"")
                .contains("\"monthDay\":\"--02-29\"")
                .contains("\"period\":\"P1Y2M3D\"")
                .contains("\"duration\":\"PT1H2M3.000000004S\"")
                .contains("\"zoneId\":\"Europe/Paris\"")
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
                YearMonth.of(2022, 10));

        String json = mapper.writeValueAsString(values);

        assertThat(json)
                .contains("\"meetingTime\":\"2022/10/05 14:30:15\"")
                .contains("\"compactDate\":\"20221005\"")
                .contains("\"billingMonth\":\"2022-10\"");

        FormattedValues restored = mapper.readValue(json, FormattedValues.class);

        assertThat(restored.meetingTime).isEqualTo(values.meetingTime);
        assertThat(restored.compactDate).isEqualTo(values.compactDate);
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

        TypeReference<LinkedHashMap<LocalDate, OffsetDateTime>> type =
                new TypeReference<LinkedHashMap<LocalDate, OffsetDateTime>>() {};
        LinkedHashMap<LocalDate, OffsetDateTime> restored = mapper.readValue(json, type);

        assertThat(restored).containsExactlyEntriesOf(deadlines);
    }

    @Test
    void supportsObjectMapperModuleAutoDiscovery() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        MonthDay leapDay = MonthDay.of(2, 29);

        String json = mapper.writeValueAsString(leapDay);

        assertThat(json).isEqualTo("\"--02-29\"");
        assertThat(mapper.readValue(json, MonthDay.class)).isEqualTo(leapDay);
    }

    @Test
    void supportsArrayAndNumericTimestampRepresentations() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        LocalDate date = LocalDate.of(2020, 2, 29);
        LocalTime time = LocalTime.of(1, 2, 3, 4);
        LocalDateTime dateTime = LocalDateTime.of(date, time);
        Instant instant = Instant.parse("2022-01-01T00:00:00.123456789Z");

        String dateJson = mapper.writeValueAsString(date);
        String timeJson = mapper.writeValueAsString(time);
        String dateTimeJson = mapper.writeValueAsString(dateTime);
        String instantJson = mapper.writeValueAsString(instant);

        assertThat(dateJson).isEqualTo("[2020,2,29]");
        assertThat(dateTimeJson).startsWith("[2020,2,29,1,2,3");
        assertThat(instantJson).doesNotStartWith("\"");
        assertThat(mapper.readValue(dateJson, LocalDate.class)).isEqualTo(date);
        assertThat(mapper.readValue(timeJson, LocalTime.class)).isEqualTo(time);
        assertThat(mapper.readValue(dateTimeJson, LocalDateTime.class)).isEqualTo(dateTime);
        assertThat(mapper.readValue(instantJson, Instant.class)).isEqualTo(instant);
    }

    @Test
    void supportsMillisecondTimestampConfigurationForInstantsAndDurations() throws Exception {
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS)
                .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
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
        ObjectMapper mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATE_TIMESTAMPS_AS_NANOSECONDS);
        DurationUnits values = new DurationUnits(Duration.ofMinutes(90), Duration.ofHours(5));

        String json = mapper.writeValueAsString(values);

        assertThat(json).contains("\"minutes\":90").contains("\"hours\":5");

        DurationUnits restored = mapper.readValue("{\"minutes\":45,\"hours\":3}", DurationUnits.class);

        assertThat(restored.minutes).isEqualTo(Duration.ofMinutes(45));
        assertThat(restored.hours).isEqualTo(Duration.ofHours(3));
    }

    private static ObjectMapper isoMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .disable(DeserializationFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .enable(SerializationFeature.WRITE_DATES_WITH_ZONE_ID);
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

        public DateTimeValues() { }

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

        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "uuuu-MM")
        public YearMonth billingMonth;

        public FormattedValues() { }

        FormattedValues(LocalDateTime meetingTime, LocalDate compactDate, YearMonth billingMonth) {
            this.meetingTime = meetingTime;
            this.compactDate = compactDate;
            this.billingMonth = billingMonth;
        }
    }

    public static final class DurationUnits {
        @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT, pattern = "MINUTES")
        public Duration minutes;

        @JsonFormat(shape = JsonFormat.Shape.NUMBER_INT, pattern = "HOURS")
        public Duration hours;

        public DurationUnits() { }

        DurationUnits(Duration minutes, Duration hours) {
            this.minutes = minutes;
            this.hours = hours;
        }
    }
}
