/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_logging;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.MonitoredResource;
import com.google.cloud.NoCredentials;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.LoggingEnhancer;
import com.google.cloud.logging.LoggingHandler;
import com.google.cloud.logging.LoggingOptions;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;

public class LoggingConfigTest {
    private static final String HANDLER_CONFIG_PREFIX = LoggingHandler.class.getName();

    @Test
    void loggingHandlerLoadsConfiguredFilterFormatterAndEnhancer() throws Exception {
        RecordingEnhancer.instances.set(0);
        LogManager logManager = LogManager.getLogManager();
        LoggingHandler handler = null;

        try {
            logManager.readConfiguration(new ByteArrayInputStream(handlerConfiguration().getBytes(StandardCharsets.UTF_8)));
            LoggingOptions options = LoggingOptions.newBuilder()
                    .setProjectId("test-project")
                    .setHost("localhost:1")
                    .setCredentials(NoCredentials.getInstance())
                    .build();
            MonitoredResource resource = MonitoredResource.of("global", Map.of("project_id", "test-project"));

            handler = new LoggingHandler("test-log", options, resource);

            assertThat(handler.getFilter()).isInstanceOf(RecordingFilter.class);
            assertThat(handler.getFormatter()).isInstanceOf(RecordingFormatter.class);
            assertThat(RecordingEnhancer.instances).hasValue(1);
        } finally {
            if (handler != null) {
                handler.close();
            }
            logManager.reset();
        }
    }

    private static String handlerConfiguration() {
        return """
                %s.filter=%s
                %s.formatter=%s
                %s.enhancers=%s
                %s.synchronicity=SYNC
                """.formatted(
                HANDLER_CONFIG_PREFIX,
                RecordingFilter.class.getName(),
                HANDLER_CONFIG_PREFIX,
                RecordingFormatter.class.getName(),
                HANDLER_CONFIG_PREFIX,
                RecordingEnhancer.class.getName(),
                HANDLER_CONFIG_PREFIX);
    }

    public static final class RecordingFilter implements Filter {
        @Override
        public boolean isLoggable(LogRecord record) {
            return true;
        }
    }

    public static final class RecordingFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            return record.getMessage();
        }
    }

    public static final class RecordingEnhancer implements LoggingEnhancer {
        private static final AtomicInteger instances = new AtomicInteger();

        public RecordingEnhancer() {
            instances.incrementAndGet();
        }

        @Override
        public void enhanceLogEntry(LogEntry.Builder builder) {
            builder.addLabel("enhanced", "true");
        }
    }
}
