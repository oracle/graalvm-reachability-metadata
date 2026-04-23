/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback_contrib.logback_json_core;

import ch.qos.logback.contrib.json.JsonFormatter;
import ch.qos.logback.contrib.json.JsonLayoutBase;
import ch.qos.logback.core.ContextBase;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class Logback_json_coreTest {

    @Test
    void doLayoutFormatsConfiguredFieldsAndAppendsLineSeparator() {
        TestJsonLayout layout = new TestJsonLayout();
        RecordingJsonFormatter formatter = new RecordingJsonFormatter();
        LinkedHashMap<String, String> context = linkedContext("requestId", "42");

        layout.setJsonFormatter(formatter);
        layout.setIncludeTimestamp(true);
        layout.setTimestampFormat("yyyy-MM-dd HH:mm:ss");
        layout.setTimestampFormatTimezoneId("UTC");
        layout.setAppendLineSeparator(true);

        String output = layout.doLayout(new TestEvent("hello", 0L, context, true, true, true));

        assertThat(layout.getContentType()).isEqualTo("application/json");
        assertThat(layout.getJsonFormatter()).isSameAs(formatter);
        assertThat(layout.isIncludeTimestamp()).isTrue();
        assertThat(layout.getTimestampFormat()).isEqualTo("yyyy-MM-dd HH:mm:ss");
        assertThat(layout.getTimestampFormatTimezoneId()).isEqualTo("UTC");
        assertThat(layout.isAppendLineSeparator()).isTrue();
        assertThat(formatter.lastMap)
                .containsEntry("message", "hello")
                .containsEntry("timestamp", "1970-01-01 00:00:00")
                .containsEntry("context", context);
        assertThat(output)
                .isEqualTo("message=hello|timestamp=1970-01-01 00:00:00|context={requestId=42}" + System.lineSeparator());
    }

    @Test
    void doLayoutFallsBackToMapToStringWhenFormatterIsMissing() {
        TestJsonLayout layout = new TestJsonLayout();
        layout.setContext(new ContextBase());

        String output = layout.doLayout(new TestEvent("hello", 0L, Collections.emptyMap(), true, false, false));

        assertThat(output).isEqualTo("{message=hello}");
    }

    @Test
    void doLayoutFallsBackToMapToStringWhenFormatterThrows() {
        TestJsonLayout layout = new TestJsonLayout();
        layout.setContext(new ContextBase());
        layout.setJsonFormatter(new ThrowingJsonFormatter());

        String output = layout.doLayout(new TestEvent("hello", 0L, Collections.emptyMap(), true, false, false));

        assertThat(output).isEqualTo("{message=hello}");
    }

    @Test
    void doLayoutReturnsNullWhenJsonMapIsNullOrEmpty() {
        TestJsonLayout layout = new TestJsonLayout();
        RecordingJsonFormatter formatter = new RecordingJsonFormatter();
        layout.setJsonFormatter(formatter);

        String nullOutput = layout.doLayout(null);
        String emptyOutput = layout.doLayout(new TestEvent("ignored", 0L, Collections.emptyMap(), false, false, false));

        assertThat(nullOutput).isNull();
        assertThat(emptyOutput).isNull();
        assertThat(formatter.calls).isZero();
    }

    @Test
    void addAndAddMapHonorInclusionRules() {
        TestJsonLayout layout = new TestJsonLayout();
        LinkedHashMap<String, Object> json = new LinkedHashMap<>();

        layout.add("message", true, "hello", json);
        layout.add("skippedNull", true, null, json);
        layout.add("skippedDisabled", false, "ignored", json);
        layout.addMap("context", true, linkedContext("requestId", "42"), json);
        layout.addMap("skippedEmptyMap", true, Collections.emptyMap(), json);
        layout.addMap("skippedDisabledMap", false, linkedContext("requestId", "99"), json);

        assertThat(json)
                .containsEntry("message", "hello")
                .containsEntry("context", linkedContext("requestId", "42"))
                .hasSize(2);
    }

    @Test
    void addTimestampUsesRawValuesUnlessFormattingIsConfigured() {
        TestJsonLayout layout = new TestJsonLayout();
        LinkedHashMap<String, Object> json = new LinkedHashMap<>();

        layout.addTimestamp("raw", true, 42L, json);
        layout.setTimestampFormat("yyyy-MM-dd");
        layout.addTimestamp("negative", true, -1L, json);
        layout.setTimestampFormatTimezoneId("UTC");
        layout.addTimestamp("formatted", true, 86_400_000L, json);
        layout.addTimestamp("skipped", false, 0L, json);

        assertThat(json)
                .containsEntry("raw", "42")
                .containsEntry("negative", "-1")
                .containsEntry("formatted", "1970-01-02")
                .doesNotContainKey("skipped");
    }

    private static LinkedHashMap<String, String> linkedContext(String key, String value) {
        LinkedHashMap<String, String> context = new LinkedHashMap<>();
        context.put(key, value);
        return context;
    }

    private record TestEvent(
            String message,
            long timestamp,
            Map<String, String> context,
            boolean includeMessage,
            boolean includeTimestamp,
            boolean includeContext
    ) {
    }

    private static final class TestJsonLayout extends JsonLayoutBase<TestEvent> {

        @Override
        protected Map<String, Object> toJsonMap(TestEvent event) {
            if (event == null) {
                return null;
            }
            LinkedHashMap<String, Object> json = new LinkedHashMap<>();
            add("message", event.includeMessage(), event.message(), json);
            addTimestamp("timestamp", event.includeTimestamp(), event.timestamp(), json);
            addMap("context", event.includeContext(), event.context(), json);
            return json;
        }
    }

    private static final class RecordingJsonFormatter implements JsonFormatter {

        private Map<String, Object> lastMap;
        private int calls;

        @Override
        @SuppressWarnings("unchecked")
        public String toJsonString(Map map) {
            calls++;
            lastMap = new LinkedHashMap<>((Map<String, Object>) map);

            StringBuilder builder = new StringBuilder();
            for (Object entryObject : map.entrySet()) {
                Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObject;
                if (builder.length() > 0) {
                    builder.append('|');
                }
                builder.append(entry.getKey()).append('=').append(entry.getValue());
            }
            return builder.toString();
        }
    }

    private static final class ThrowingJsonFormatter implements JsonFormatter {

        @Override
        public String toJsonString(Map map) throws Exception {
            throw new Exception("boom");
        }
    }
}
