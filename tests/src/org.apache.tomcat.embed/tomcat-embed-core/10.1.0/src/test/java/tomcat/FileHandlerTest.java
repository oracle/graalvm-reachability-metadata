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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;

import org.apache.juli.FileHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class FileHandlerTest {
    @TempDir
    Path directory;

    @Test
    void constructsConfiguredFilterAndFormatterAndWritesAcceptedRecords() throws Exception {
        LogManager manager = LogManager.getLogManager();
        String prefix = FileHandler.class.getName();
        Map<String, String> previous = new HashMap<>();
        previous.put(prefix + ".filter", manager.getProperty(prefix + ".filter"));
        previous.put(prefix + ".formatter", manager.getProperty(prefix + ".formatter"));
        String configuration = prefix + ".filter=" + AcceptedFilter.class.getName() + "\n"
                + prefix + ".formatter=" + MessageFormatter.class.getName() + "\n";
        manager.updateConfiguration(new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8)),
                key -> (oldValue, newValue) -> newValue == null ? oldValue : newValue);
        FileHandler handler = new FileHandler(directory.toString(), "application", ".log", -1, false, -1);
        try {
            handler.publish(new LogRecord(Level.INFO, "accepted-event"));
            handler.publish(new LogRecord(Level.INFO, "ignored-event"));
            handler.flush();
            assertThat(Files.readString(directory.resolve("application.log"))).isEqualTo("accepted-event\n");
        } finally {
            handler.close();
            manager.updateConfiguration(new ByteArrayInputStream(new byte[0]),
                    key -> (oldValue, newValue) -> previous.containsKey(key) ? previous.get(key) : oldValue);
        }
    }

    public static class AcceptedFilter implements Filter {
        @Override
        public boolean isLoggable(LogRecord record) {
            return record.getMessage().startsWith("accepted");
        }
    }

    public static class MessageFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            return record.getMessage() + "\n";
        }
    }
}
