/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat.tomcat_juli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.apache.juli.FileHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileHandlerTest {
    @TempDir
    Path logDirectory;

    @AfterEach
    void resetLogManager() {
        LogManager.getLogManager().reset();
    }

    @Test
    void configuresCustomFilterAndFormatterFromLogManagerProperties() throws Exception {
        loadLoggingProperties("""
                org.apache.juli.FileHandler.filter=%s
                org.apache.juli.FileHandler.formatter=%s
                org.apache.juli.FileHandler.level=ALL
                """.formatted(RecordingFilter.class.getName(), RecordingFormatter.class.getName()));

        FileHandler handler = new FileHandler(logDirectory.toString(), "coverage-", ".log", 0, false, -1);
        try {
            assertThat(handler.getFilter()).isInstanceOf(RecordingFilter.class);
            assertThat(handler.getFormatter()).isInstanceOf(RecordingFormatter.class);

            LogRecord record = new LogRecord(Level.INFO, "message from FileHandler");
            handler.publish(record);
            handler.flush();

            RecordingFilter filter = (RecordingFilter) handler.getFilter();
            RecordingFormatter formatter = (RecordingFormatter) handler.getFormatter();
            assertThat(filter.acceptedRecords).isEqualTo(1);
            assertThat(formatter.formattedRecords).isEqualTo(1);
            assertThat(Files.readString(logDirectory.resolve("coverage-.log")))
                    .contains("formatted: message from FileHandler");
        } finally {
            handler.close();
        }
    }

    private static void loadLoggingProperties(String properties) throws Exception {
        byte[] bytes = properties.getBytes(StandardCharsets.UTF_8);
        LogManager.getLogManager().readConfiguration(new ByteArrayInputStream(bytes));
    }

    public static final class RecordingFilter implements Filter {
        private int acceptedRecords;

        public RecordingFilter() {
        }

        @Override
        public boolean isLoggable(LogRecord record) {
            acceptedRecords++;
            return true;
        }
    }

    public static final class RecordingFormatter extends Formatter {
        private int formattedRecords;

        public RecordingFormatter() {
        }

        @Override
        public String format(LogRecord record) {
            formattedRecords++;
            return "formatted: " + record.getMessage() + System.lineSeparator();
        }
    }
}
