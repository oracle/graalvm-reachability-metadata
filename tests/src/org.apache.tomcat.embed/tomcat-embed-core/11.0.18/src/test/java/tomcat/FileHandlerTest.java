/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.apache.juli.FileHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class FileHandlerTest {

    @Test
    void configuresFilterAndFormatterFromLogManagerProperties(@TempDir Path logDirectory) throws Exception {
        String configuration = """
                org.apache.juli.FileHandler.directory=%s
                org.apache.juli.FileHandler.prefix=reachability-
                org.apache.juli.FileHandler.suffix=.log
                org.apache.juli.FileHandler.rotatable=false
                org.apache.juli.FileHandler.bufferSize=-1
                org.apache.juli.FileHandler.level=ALL
                org.apache.juli.FileHandler.filter=tomcat.FileHandlerTest$AcceptingFilter
                org.apache.juli.FileHandler.formatter=tomcat.FileHandlerTest$ReachabilityFormatter
                """.formatted(logDirectory.toAbsolutePath());
        LogManager.getLogManager().readConfiguration(
                new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8)));

        FileHandler handler = new FileHandler();
        try {
            LogRecord ignoredRecord = new LogRecord(java.util.logging.Level.INFO, "ignored");
            LogRecord acceptedRecord = new LogRecord(java.util.logging.Level.INFO, "accepted message");

            handler.publish(ignoredRecord);
            handler.publish(acceptedRecord);
            handler.flush();

            Path logFile = logDirectory.resolve("reachability-.log");
            assertThat(handler.getFilter()).isInstanceOf(AcceptingFilter.class);
            assertThat(handler.getFormatter()).isInstanceOf(ReachabilityFormatter.class);
            assertThat(Files.readString(logFile)).isEqualTo("formatted:accepted message" + System.lineSeparator());
        } finally {
            handler.close();
            LogManager.getLogManager().readConfiguration();
        }
    }

    public static class AcceptingFilter implements Filter {

        public AcceptingFilter() {
        }

        @Override
        public boolean isLoggable(LogRecord record) {
            return record.getMessage().contains("accepted");
        }
    }

    public static class ReachabilityFormatter extends Formatter {

        public ReachabilityFormatter() {
        }

        @Override
        public String format(LogRecord record) {
            return "formatted:" + record.getMessage() + System.lineSeparator();
        }
    }
}
