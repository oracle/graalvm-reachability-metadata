/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_perfmark.perfmark_api;

import io.perfmark.Link;
import io.perfmark.PerfMark;
import io.perfmark.Tag;
import io.perfmark.TaskCloseable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PerfMarkTest {

    @Test
    void debugInitializationLogsMissingImplementationAndNoopApisRemainUsable() {
        String debugPropertyName = "io.perfmark.PerfMark.debug";
        String previousDebugProperty = System.getProperty(debugPropertyName);
        Logger perfMarkLogger = Logger.getLogger("io.perfmark.PerfMark");
        Level previousLevel = perfMarkLogger.getLevel();
        boolean previousUseParentHandlers = perfMarkLogger.getUseParentHandlers();
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };

        perfMarkLogger.addHandler(handler);
        perfMarkLogger.setLevel(Level.FINE);
        perfMarkLogger.setUseParentHandlers(false);
        System.setProperty(debugPropertyName, "true");

        try {
            Tag requestTag = PerfMark.createTag("request", 42L);
            assertThat(requestTag).isNotNull();

            PerfMark.setEnabled(true);
            PerfMark.startTask("request", requestTag);
            PerfMark.attachTag("status", "accepted");
            PerfMark.attachTag("attempt", 1L);
            PerfMark.attachTag("trace uuid", 1L, 2L);
            PerfMark.attachTag("payload", "body", value -> "recorded-" + value);

            Link link = PerfMark.linkOut();
            assertThat(link).isNotNull();
            PerfMark.linkIn(link);

            PerfMark.event("request-event", requestTag);
            PerfMark.event("request-event");
            PerfMark.event("request-event", "sub-event");

            try (TaskCloseable ignored = PerfMark.traceTask("closeable-task")) {
                PerfMark.attachTag(PerfMark.createTag());
            }
            try (TaskCloseable ignored = PerfMark.traceTask(123, Object::toString)) {
                PerfMark.attachTag("number", 123L);
            }

            PerfMark.stopTask("request", requestTag);
            PerfMark.setEnabled(false);

            assertThat(records).anySatisfy(record -> {
                assertThat(record.getLevel()).isEqualTo(Level.FINE);
                assertThat(record.getMessage()).isEqualTo("Error during PerfMark.<clinit>");
                assertThat(record.getThrown()).isNotNull();
            });
        } finally {
            perfMarkLogger.removeHandler(handler);
            perfMarkLogger.setLevel(previousLevel);
            perfMarkLogger.setUseParentHandlers(previousUseParentHandlers);
            if (previousDebugProperty == null) {
                System.clearProperty(debugPropertyName);
            } else {
                System.setProperty(debugPropertyName, previousDebugProperty);
            }
        }
    }
}
