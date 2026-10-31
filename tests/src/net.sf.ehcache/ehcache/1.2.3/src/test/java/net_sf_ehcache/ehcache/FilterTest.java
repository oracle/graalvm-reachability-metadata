/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_ehcache.ehcache;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import javax.servlet.Filter;
import javax.servlet.ServletException;
import net.sf.ehcache.constructs.web.filter.GzipFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Timeout(120)
@ResourceLock("filter-logging")
public class FilterTest {
    @Test
    void logsConfiguredExceptionWithoutStackTrace() throws Exception {
        assertConfiguredLogging(true);
    }

    @Test
    void logsConfiguredExceptionWithStackTrace() throws Exception {
        assertConfiguredLogging(false);
    }

    private void assertConfiguredLogging(boolean suppress) throws Exception {
        Logger logger = Logger.getLogger("net.sf.ehcache.constructs.web.filter.Filter");
        Level originalLevel = logger.getLevel();
        List<LogRecord> records = new ArrayList<>();
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }
            @Override
            public void flush() { }
            @Override
            public void close() { }
        };
        handler.setLevel(Level.ALL);
        logger.addHandler(handler);
        logger.setLevel(Level.ALL);
        Filter filter = new GzipFilter();
        IOException failure = new IOException("downstream service unavailable");
        WebContext config = new WebContext();
        config.parameters.put("exceptionsToLogDifferently", IOException.class.getName());
        config.parameters.put("exceptionsToLogDifferentlyLevel", "info");
        config.parameters.put("suppressStackTraces", Boolean.toString(suppress));
        try {
            filter.init(config);
            ServletException thrown = assertThrows(ServletException.class,
                    () -> filter.doFilter(new WebRequest(), new WebResponse(), (request, response) -> {
                        throw failure;
                    }));
            assertSame(failure, thrown.getRootCause());
            assertTrue(thrown.getMessage().contains("/orders"));
            assertTrue(thrown.getMessage().contains("view=summary"));
            List<LogRecord> matching = records.stream()
                    .filter(record -> failure.getMessage().equals(record.getMessage())).toList();
            assertEquals(1, matching.size());
            assertEquals(Level.INFO, matching.get(0).getLevel());
            assertSame(suppress ? null : failure, matching.get(0).getThrown());
        } finally {
            filter.destroy();
            logger.removeHandler(handler);
            logger.setLevel(originalLevel);
        }
    }
}
