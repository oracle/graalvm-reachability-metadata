/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.apache.juli.ClassLoaderLogManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderLogManagerTest {
    @Test
    void instantiatesAndRoutesToConfiguredLogHandler() throws Exception {
        RecordingHandler.messages.clear();
        ClassLoaderLogManager manager = new ClassLoaderLogManager();
        manager.setUseShutdownHook(false);
        String configuration = "handlers=" + RecordingHandler.class.getName() + "\n.level=INFO\n";
        try {
            manager.readConfiguration(new ByteArrayInputStream(configuration.getBytes(StandardCharsets.UTF_8)));
            Logger logger = Logger.getLogger("tomcat.classloader.logging");
            manager.addLogger(logger);
            logger.info("configuration-loaded");
            assertThat(RecordingHandler.messages).contains("configuration-loaded");
        } finally {
            manager.reset();
        }
    }

    public static class RecordingHandler extends Handler {
        private static final List<String> messages = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
            // Records are retained synchronously in memory.
        }

        @Override
        public void close() {
            flush();
        }
    }
}
