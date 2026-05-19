/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tools_jackson_datatype.jackson_datatype_joda;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.Days;
import org.joda.time.Duration;
import org.joda.time.Hours;
import org.joda.time.Instant;
import org.joda.time.Interval;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.Minutes;
import org.joda.time.MonthDay;
import org.joda.time.Months;
import org.joda.time.Period;
import org.joda.time.ReadableDateTime;
import org.joda.time.ReadableInstant;
import org.joda.time.ReadablePeriod;
import org.joda.time.Seconds;
import org.joda.time.Weeks;
import org.joda.time.YearMonth;
import org.joda.time.Years;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.datatype.joda.JodaModule;

import static org.assertj.core.api.Assertions.assertThat;

public class Jackson_datatype_jodaTest {
    @Test
    void moduleExposesNameAndVersion() {
        JodaModule module = new JodaModule();

        assertThat(module.getModuleName()).isEqualTo(JodaModule.class.getName());
        assertThat(module.version()).isNotNull();
    }

    @Test
    void serializesAndDeserializesJodaTypesAsIsoStrings() throws Exception {
        ObjectMapper mapper = isoStringMapper();
        TemporalPayload payload = TemporalPayload.sample();

        String json = mapper.writeValueAsString(payload);
        JsonNode tree = mapper.readTree(json);

        assertThat(tree.path("dateTime").asString()).contains("[Europe/Paris]");
        assertThat(tree.path("localDate").asString()).isEqualTo(payload.localDate.toString());
        assertThat(tree.path("localDateTime").asString()).isEqualTo(payload.localDateTime.toString());
        assertThat(tree.path("localTime").asString()).isEqualTo(payload.localTime.toString());
        assertThat(tree.path("dateTimeZone").asString()).isEqualTo(payload.dateTimeZone.getID());
        assertThat(tree.path("duration").asString()).isEqualTo(payload.duration.toString());
        assertThat(tree.path("period").asString()).isEqualTo(payload.period.toString());
        assertThat(tree.path("yearMonth").asString()).isEqualTo(payload.yearMonth.toString());
        assertThat(tree.path("monthDay").asString()).isEqualTo(payload.monthDay.toString());

        TemporalPayload roundTripped = mapper.readValue(json, TemporalPayload.class);

        assertThat(roundTripped.dateTime).isEqualTo(payload.dateTime);
        assertThat(roundTripped.instant).isEqualTo(payload.instant);
        assertThat(roundTripped.interval.getStartMillis()).isEqualTo(payload.interval.getStartMillis());
        assertThat(roundTripped.interval.getEndMillis()).isEqualTo(payload.interval.getEndMillis());
        assertThat(roundTripped.localDate).isEqualTo(payload.localDate);
        assertThat(roundTripped.localDateTime).isEqualTo(payload.localDateTime);
        assertThat(roundTripped.localTime).isEqualTo(payload.localTime);
        assertThat(roundTripped.dateTimeZone).isEqualTo(payload.dateTimeZone);
        assertThat(roundTripped.duration).isEqualTo(payload.duration);
        assertThat(roundTripped.period).isEqualTo(payload.period);
        assertThat(roundTripped.yearMonth).isEqualTo(payload.yearMonth);
        assertThat(roundTripped.monthDay).isEqualTo(payload.monthDay);
    }

    @Test
    void supportsTimestampAndArrayShapesForJodaValues() throws Exception {
        ObjectMapper mapper = timestampMapper();

        assertThat(mapper.writeValueAsString(new DateTime(1_714_976_889_010L, DateTimeZone.UTC)))
                .isEqualTo("1714976889010");
        assertThat(mapper.readValue("1714976889010", DateTime.class))
                .isEqualTo(new DateTime(1_714_976_889_010L, DateTimeZone.UTC));

        assertThat(mapper.writeValueAsString(new LocalDate(2024, 5, 6))).isEqualTo("[2024,5,6]");
        assertThat(mapper.readValue("[2024,5,6]", LocalDate.class)).isEqualTo(new LocalDate(2024, 5, 6));

        assertThat(mapper.writeValueAsString(new LocalDateTime(2024, 5, 6, 7, 8, 9, 10)))
                .isEqualTo("[2024,5,6,7,8,9,10]");
        assertThat(mapper.readValue("[2024,5,6,7,8,9,10]", LocalDateTime.class))
                .isEqualTo(new LocalDateTime(2024, 5, 6, 7, 8, 9, 10));

        assertThat(mapper.writeValueAsString(new LocalTime(7, 8, 9, 10))).isEqualTo("[7,8,9,10]");
        assertThat(mapper.readValue("[7,8,9,10]", LocalTime.class)).isEqualTo(new LocalTime(7, 8, 9, 10));

        assertThat(mapper.writeValueAsString(new Duration(90_000L))).isEqualTo("90000");
        assertThat(mapper.readValue("90000", Duration.class)).isEqualTo(new Duration(90_000L));
    }

    @Test
    void readsRegisteredJodaMapKeyTypes() throws Exception {
        ObjectMapper mapper = isoStringMapper();

        Map<DateTime, String> dateTimeKeys = readMapWithKeys(mapper,
                "{\"2024-05-06T07:08:09.010+02:00\":\"event\"}", DateTime.class);
        Map.Entry<DateTime, String> dateTimeEntry = dateTimeKeys.entrySet().iterator().next();
        assertThat(dateTimeEntry.getKey().getMillis())
                .isEqualTo(new DateTime("2024-05-06T07:08:09.010+02:00").getMillis());
        assertThat(dateTimeEntry.getValue()).isEqualTo("event");

        Map<LocalDate, String> localDateKeys = readMapWithKeys(mapper,
                "{\"2024-05-06\":\"date\"}", LocalDate.class);
        assertThat(localDateKeys).containsEntry(new LocalDate(2024, 5, 6), "date");

        Map<LocalDateTime, String> localDateTimeKeys = readMapWithKeys(mapper,
                "{\"2024-05-06T07:08:09.010\":\"dateTime\"}", LocalDateTime.class);
        assertThat(localDateTimeKeys).containsEntry(new LocalDateTime(2024, 5, 6, 7, 8, 9, 10), "dateTime");

        Map<LocalTime, String> localTimeKeys = readMapWithKeys(mapper,
                "{\"07:08:09.010\":\"time\"}", LocalTime.class);
        assertThat(localTimeKeys).containsEntry(new LocalTime(7, 8, 9, 10), "time");

        String durationJson = "{\"" + new Duration(90_000L) + "\":\"duration\"}";
        Map<Duration, String> durationKeys = readMapWithKeys(mapper, durationJson, Duration.class);
        assertThat(durationKeys).containsEntry(new Duration(90_000L), "duration");

        Map<Period, String> periodKeys = readMapWithKeys(mapper,
                "{\"P2D\":\"period\"}", Period.class);
        assertThat(periodKeys).containsEntry(Period.days(2), "period");
    }

    @Test
    void serializesJodaAmountTypesRegisteredByModule() throws Exception {
        ObjectMapper mapper = isoStringMapper();
        AmountPayload payload = AmountPayload.sample();

        JsonNode tree = mapper.readTree(mapper.writeValueAsString(payload));

        assertThat(tree.path("days").asInt()).isEqualTo(2);
        assertThat(tree.path("hours").asInt()).isEqualTo(3);
        assertThat(tree.path("minutes").asInt()).isEqualTo(4);
        assertThat(tree.path("months").asInt()).isEqualTo(5);
        assertThat(tree.path("seconds").asInt()).isEqualTo(6);
        assertThat(tree.path("weeks").asInt()).isEqualTo(7);
        assertThat(tree.path("years").asInt()).isEqualTo(8);
    }

    @Test
    void parsesScalarValuesWithoutBeanProperties() throws Exception {
        ObjectMapper mapper = isoStringMapper();

        assertThat(mapper.readValue("\"Europe/Paris\"", DateTimeZone.class))
                .isEqualTo(DateTimeZone.forID("Europe/Paris"));
        Interval interval = mapper.readValue(
                "\"2024-05-06T07:08:09.010+02:00/2024-05-06T08:08:09.010+02:00\"", Interval.class);
        assertThat(interval.getStartMillis()).isEqualTo(new DateTime("2024-05-06T07:08:09.010+02:00").getMillis());
        assertThat(interval.getEndMillis()).isEqualTo(new DateTime("2024-05-06T08:08:09.010+02:00").getMillis());
        assertThat(mapper.readValue("\"2024-05\"", YearMonth.class)).isEqualTo(new YearMonth(2024, 5));
        assertThat(mapper.readValue("\"--05-06\"", MonthDay.class)).isEqualTo(new MonthDay(5, 6));

        ReadableDateTime readableDateTime = mapper.readValue("\"2024-05-06T07:08:09.010+02:00\"",
                ReadableDateTime.class);
        assertThat(readableDateTime.getMillis()).isEqualTo(new DateTime("2024-05-06T07:08:09.010+02:00").getMillis());
        ReadableInstant readableInstant = mapper.readValue("\"2024-05-06T07:08:09.010+02:00\"",
                ReadableInstant.class);
        assertThat(readableInstant.getMillis()).isEqualTo(new DateTime("2024-05-06T07:08:09.010+02:00").getMillis());
        ReadablePeriod readablePeriod = mapper.readValue("\"PT2H\"", ReadablePeriod.class);
        assertThat(readablePeriod).isEqualTo(Period.hours(2));
    }

    @Test
    void honorsJsonFormatPatternsOnJodaProperties() throws Exception {
        ObjectMapper mapper = formattingMapper();
        FormattedPayload payload = FormattedPayload.sample();

        String json = mapper.writeValueAsString(payload);
        JsonNode tree = mapper.readTree(json);

        assertThat(tree.path("dateTime").asString()).isEqualTo("2024/05/06 07:08:09 +0200");
        assertThat(tree.path("localDate").asString()).isEqualTo("06.05.2024");
        assertThat(tree.path("localTime").asString()).isEqualTo("07*08*09");

        FormattedPayload parsed = mapper.readValue("""
                {"dateTime":"2024/05/06 07:08:09 +0200","localDate":"06.05.2024","localTime":"07*08*09"}
                """, FormattedPayload.class);

        assertThat(parsed.dateTime.getMillis()).isEqualTo(payload.dateTime.getMillis());
        assertThat(parsed.localDate).isEqualTo(payload.localDate);
        assertThat(parsed.localTime).isEqualTo(payload.localTime);
    }

    private static <K> Map<K, String> readMapWithKeys(ObjectMapper mapper, String json, Class<K> keyType)
            throws Exception {
        return mapper.readValue(json,
                mapper.getTypeFactory().constructMapType(LinkedHashMap.class, keyType, String.class));
    }

    private static ObjectMapper isoStringMapper() {
        return JsonMapper.builder()
                .addModule(new JodaModule())
                .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .enable(DateTimeFeature.WRITE_DATES_WITH_ZONE_ID)
                .defaultTimeZone(TimeZone.getTimeZone("UTC"))
                .build();
    }

    private static ObjectMapper timestampMapper() {
        return JsonMapper.builder()
                .addModule(new JodaModule())
                .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .enable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(DateTimeFeature.WRITE_DURATIONS_AS_TIMESTAMPS)
                .defaultTimeZone(TimeZone.getTimeZone("UTC"))
                .build();
    }

    private static ObjectMapper formattingMapper() {
        return JsonMapper.builder()
                .addModule(new JodaModule())
                .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DateTimeFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
                .defaultTimeZone(TimeZone.getTimeZone("UTC"))
                .build();
    }

    public static final class TemporalPayload {
        public DateTime dateTime;
        public Instant instant;
        public Interval interval;
        public LocalDate localDate;
        public LocalDateTime localDateTime;
        public LocalTime localTime;
        public DateTimeZone dateTimeZone;
        public Duration duration;
        public Period period;
        public YearMonth yearMonth;
        public MonthDay monthDay;

        public TemporalPayload() {
        }

        static TemporalPayload sample() {
            DateTimeZone paris = DateTimeZone.forID("Europe/Paris");
            DateTime start = new DateTime(2024, 5, 6, 7, 8, 9, 10, paris);
            TemporalPayload payload = new TemporalPayload();
            payload.dateTime = start;
            payload.instant = new Instant(start.getMillis());
            payload.interval = new Interval(start, start.plusHours(1));
            payload.localDate = new LocalDate(2024, 5, 6);
            payload.localDateTime = new LocalDateTime(2024, 5, 6, 7, 8, 9, 10);
            payload.localTime = new LocalTime(7, 8, 9, 10);
            payload.dateTimeZone = paris;
            payload.duration = new Duration(90_000L);
            payload.period = new Period(1, 2, 0, 3, 4, 5, 6, 0);
            payload.yearMonth = new YearMonth(2024, 5);
            payload.monthDay = new MonthDay(5, 6);
            return payload;
        }
    }

    public static final class AmountPayload {
        public Days days;
        public Hours hours;
        public Minutes minutes;
        public Months months;
        public Seconds seconds;
        public Weeks weeks;
        public Years years;

        public AmountPayload() {
        }

        static AmountPayload sample() {
            AmountPayload payload = new AmountPayload();
            payload.days = Days.days(2);
            payload.hours = Hours.hours(3);
            payload.minutes = Minutes.minutes(4);
            payload.months = Months.months(5);
            payload.seconds = Seconds.seconds(6);
            payload.weeks = Weeks.weeks(7);
            payload.years = Years.years(8);
            return payload;
        }
    }

    public static final class FormattedPayload {
        @JsonFormat(pattern = "yyyy/MM/dd HH:mm:ss Z")
        public DateTime dateTime;

        @JsonFormat(pattern = "dd.MM.yyyy")
        public LocalDate localDate;

        @JsonFormat(pattern = "HH*mm*ss")
        public LocalTime localTime;

        public FormattedPayload() {
        }

        static FormattedPayload sample() {
            FormattedPayload payload = new FormattedPayload();
            payload.dateTime = new DateTime(2024, 5, 6, 7, 8, 9, DateTimeZone.forID("Europe/Paris"));
            payload.localDate = new LocalDate(2024, 5, 6);
            payload.localTime = new LocalTime(7, 8, 9);
            return payload;
        }
    }
}
