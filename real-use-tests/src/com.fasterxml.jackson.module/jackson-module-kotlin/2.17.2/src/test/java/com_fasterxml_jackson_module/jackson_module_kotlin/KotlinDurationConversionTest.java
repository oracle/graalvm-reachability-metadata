/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.kotlin.KotlinFeature;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import java.util.ArrayList;
import java.util.List;
import kotlin.time.Duration;
import org.junit.jupiter.api.Test;

/// Kotlin `kotlin.time.Duration` values bound as ISO-8601 java.time durations through the
/// Kotlin module's `UseJavaDurationConversion` feature.
///
/// A Java job scheduler shares its job definitions with Kotlin workers, which declare their
/// timeouts and retry back-offs as Kotlin `Duration`s. The scheduler enables
/// `UseJavaDurationConversion` together with the java.time module, so every duration crosses
/// the wire as an ISO-8601 string that both sides understand. It reads the definitions,
/// converts the Kotlin durations to `java.time.Duration` to plan the runs, tightens a timeout,
/// and writes the definition back.
class KotlinDurationConversionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new KotlinModule.Builder()
                    .enable(KotlinFeature.UseJavaDurationConversion)
                    .build())
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);

    /// One job definition as the Kotlin workers publish it.
    public static final class JobDefinition {
        public String name;
        public Duration timeout;
        public List<Duration> retryBackoffs;
    }

    private static java.time.Duration toJava(Duration duration) {
        return MAPPER.convertValue(duration, java.time.Duration.class);
    }

    private static Duration toKotlin(java.time.Duration duration) {
        return MAPPER.convertValue(duration, Duration.class);
    }

    @Test
    void readsKotlinDurationsFromIsoStrings() throws Exception {
        String json = """
                {
                  "name": "nightly-report",
                  "timeout": "PT1H30M",
                  "retryBackoffs": ["PT30S", "PT5S", "PT2M"]
                }
                """;

        JobDefinition job = MAPPER.readValue(json, JobDefinition.class);

        assertEquals("nightly-report", job.name);
        assertEquals("1h 30m", job.timeout.toString());
        assertEquals(java.time.Duration.ofMinutes(90), toJava(job.timeout));

        List<Duration> backoffs = new ArrayList<>(job.retryBackoffs);
        backoffs.sort(null);
        assertEquals(List.of("5s", "30s", "2m"), backoffs.stream().map(Duration::toString).toList());

        java.time.Duration totalBackoff = job.retryBackoffs.stream()
                .map(KotlinDurationConversionTest::toJava)
                .reduce(java.time.Duration.ZERO, java.time.Duration::plus);
        assertEquals(java.time.Duration.ofSeconds(155), totalBackoff);
    }

    @Test
    void writesTightenedTimeoutBackAsIsoString() throws Exception {
        JobDefinition job = MAPPER.readValue("""
                {"name": "ingest", "timeout": "PT10M", "retryBackoffs": ["PT1S"]}
                """, JobDefinition.class);

        job.timeout = toKotlin(toJava(job.timeout).minusSeconds(150));
        job.retryBackoffs = List.of(toKotlin(java.time.Duration.ofMillis(1500)), job.retryBackoffs.get(0));

        String published = MAPPER.writeValueAsString(job);

        JsonNode tree = MAPPER.readTree(published);
        assertEquals("PT7M30S", tree.get("timeout").asText());
        assertEquals("PT1.5S", tree.get("retryBackoffs").get(0).asText());
        assertEquals("PT1S", tree.get("retryBackoffs").get(1).asText());

        JobDefinition reread = MAPPER.readValue(published, JobDefinition.class);
        assertEquals(job.timeout, reread.timeout);
        assertEquals("7m 30s", reread.timeout.toString());
        assertEquals(job.retryBackoffs, reread.retryBackoffs);
    }

    @Test
    void writesAStandaloneKotlinDurationAsJavaDuration() throws Exception {
        Duration grace = toKotlin(java.time.Duration.ofHours(26).plusSeconds(1));

        assertEquals("\"PT26H1S\"", MAPPER.writeValueAsString(grace));
        assertEquals("1d 2h 0m 1s", grace.toString());
        assertEquals(java.time.Duration.ofSeconds(93601), toJava(grace));
    }

    @Test
    void rejectsADurationThatIsNotIso8601() {
        JsonMappingException rejected = assertThrows(JsonMappingException.class,
                () -> MAPPER.readValue("{\"timeout\": \"ten minutes\"}", JobDefinition.class));
        assertTrue(rejected.getMessage().contains("ten minutes"), rejected.getMessage());
    }
}
