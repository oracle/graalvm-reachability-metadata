/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_module.jackson_module_kotlin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.module.kotlin.KotlinFeature;
import com.fasterxml.jackson.module.kotlin.KotlinModule;
import com.fasterxml.jackson.module.kotlin.MissingKotlinParameterException;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import kotlin.time.TimedValue;
import org.junit.jupiter.api.Test;

/// Generic Kotlin data classes with a value-class component, `kotlin.time.TimedValue`, bound
/// through its name-mangled `Duration` getter and constructor parameter by the Kotlin module.
///
/// A Java build dashboard collects step timings from Kotlin build agents, which wrap each
/// build step in `measureTimedValue` and publish the resulting `TimedValue` with the step's
/// own result as `value`. The agents and the dashboard enable `UseJavaDurationConversion`
/// with the java.time module, so the elapsed time crosses the wire as an ISO-8601 string. The
/// dashboard reads the timings for differently typed step results, ranks the slowest steps,
/// and republishes a timing to the next dashboard in the chain.
class KotlinTimedValueBindingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new KotlinModule.Builder()
                    .enable(KotlinFeature.UseJavaDurationConversion)
                    .build())
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DURATIONS_AS_TIMESTAMPS);

    /// The result of one compile step as the agents report it.
    public static final class CompileReport {
        public String module;
        public int warnings;
        public List<String> artifacts;
    }

    /// One agent's timings for a build, keyed by the kind of step.
    public static final class BuildTimings {
        public String agent;
        public TimedValue<CompileReport> compile;
        public TimedValue<Map<String, Integer>> tests;
        public List<TimedValue<String>> uploads;
    }

    private static final String BUILD = """
            {
              "agent": "ci-linux-3",
              "compile": {
                "value": {"module": "core", "warnings": 2, "artifacts": ["core.jar", "core-sources.jar"]},
                "duration": "PT1M12.5S"
              },
              "tests": {
                "value": {"passed": 412, "failed": 3, "skipped": 9},
                "duration": "PT3M4S"
              },
              "uploads": [
                {"value": "core.jar", "duration": "PT0.75S"},
                {"value": "core-sources.jar", "duration": "PT0.25S"},
                {"value": "test-report.zip", "duration": "PT2S"}
              ]
            }
            """;

    @Test
    void readsTimingsOfDifferentlyTypedSteps() throws Exception {
        BuildTimings build = MAPPER.readValue(BUILD, BuildTimings.class);

        assertEquals("ci-linux-3", build.agent);
        CompileReport compile = build.compile.getValue();
        assertEquals("core", compile.module);
        assertEquals(2, compile.warnings);
        assertEquals(List.of("core.jar", "core-sources.jar"), compile.artifacts);
        assertEquals(Duration.ofMillis(72_500), elapsed(build.compile));

        Map<String, Integer> tests = build.tests.getValue();
        assertEquals(424, tests.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(3, tests.get("failed"));
        assertEquals("TimedValue(value={passed=412, failed=3, skipped=9}, duration=3m 4s)",
                build.tests.toString());

        assertEquals(List.of("core.jar", "core-sources.jar", "test-report.zip"),
                build.uploads.stream().map(TimedValue::getValue).toList());
        assertEquals(Duration.ofSeconds(3), build.uploads.stream()
                .map(KotlinTimedValueBindingTest::elapsed)
                .reduce(Duration.ZERO, Duration::plus));
    }

    @Test
    void ranksTheSlowestUploads() throws Exception {
        List<TimedValue<String>> uploads = MAPPER.readValue("""
                [
                  {"value": "docs.zip", "duration": "PT45S"},
                  {"value": "core.jar", "duration": "PT1.5S"},
                  {"value": "app.tar.gz", "duration": "PT2M"},
                  {"value": "checksums.txt", "duration": "PT0.01S"}
                ]
                """, new TypeReference<List<TimedValue<String>>>() {
                });

        List<String> slowestFirst = uploads.stream()
                .sorted(Comparator.comparing(KotlinTimedValueBindingTest::elapsed).reversed())
                .map(upload -> upload.getValue() + " " + upload.toString().replaceAll(".*duration=(.*)\\)", "$1"))
                .toList();

        assertEquals(List.of(
                "app.tar.gz 2m",
                "docs.zip 45s",
                "core.jar 1.5s",
                "checksums.txt 10ms"), slowestFirst);
    }

    @Test
    void republishesATimingToTheNextDashboard() throws Exception {
        BuildTimings build = MAPPER.readValue(BUILD, BuildTimings.class);

        String json = MAPPER.writeValueAsString(build.compile);

        JsonNode tree = MAPPER.readTree(json);
        assertEquals("PT1M12.5S", tree.get("duration").asText());
        assertEquals("core", tree.at("/value/module").asText());
        assertEquals(2, tree.size());

        TimedValue<CompileReport> received = MAPPER.readValue(json,
                new TypeReference<TimedValue<CompileReport>>() {
                });
        assertEquals(elapsed(build.compile), elapsed(received));
        assertEquals(build.compile.getValue().artifacts, received.getValue().artifacts);

        TimedValue<String> upload = build.uploads.get(0);
        TimedValue<String> echoed = MAPPER.readValue(MAPPER.writeValueAsString(upload),
                new TypeReference<TimedValue<String>>() {
                });
        assertEquals(upload, echoed);
        assertNotEquals(build.uploads.get(1), echoed);
    }

    @Test
    void rejectsATimingWithoutItsDuration() {
        MissingKotlinParameterException missing = assertThrows(MissingKotlinParameterException.class,
                () -> MAPPER.readValue("""
                        {"value": "orphan.jar"}
                        """, new TypeReference<TimedValue<String>>() {
                        }));

        assertEquals("duration", missing.getParameter().getName());
    }

    /// Reads the elapsed time of a step the way the dashboard charts it, as a java.time value.
    private static Duration elapsed(TimedValue<?> timed) {
        JsonNode tree = MAPPER.valueToTree(timed);
        return Duration.parse(tree.get("duration").asText());
    }
}
