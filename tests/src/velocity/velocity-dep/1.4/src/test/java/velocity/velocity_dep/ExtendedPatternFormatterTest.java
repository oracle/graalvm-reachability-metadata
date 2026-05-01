/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import java.io.ByteArrayOutputStream;

import org.apache.log.ContextMap;
import org.apache.log.Hierarchy;
import org.apache.log.LogEvent;
import org.apache.log.Logger;
import org.apache.log.Priority;
import org.apache.log.format.ExtendedPatternFormatter;
import org.apache.log.output.io.StreamTarget;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ExtendedPatternFormatterTest {
    @Test
    @Order(1)
    void formatsMethodPatternWhenEventHasNoMethodContext() {
        ExtendedPatternFormatter formatter = new ExtendedPatternFormatter("method=%{method}");
        LogEvent logEvent = new LogEvent();
        logEvent.setContextMap(new ContextMap());

        String formattedEvent = formatter.format(logEvent);

        assertEquals("method=", formattedEvent);
    }

    @Test
    @Order(2)
    void formatsMethodPatternWhileProcessingLoggerEvent() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ExtendedPatternFormatter formatter = new ExtendedPatternFormatter("method=%{method} message=%{message}");
        StreamTarget logTarget = new StreamTarget(outputStream, formatter);
        Hierarchy hierarchy = new Hierarchy();
        hierarchy.setDefaultLogTarget(logTarget);
        hierarchy.setDefaultPriority(Priority.DEBUG);
        Logger logger = hierarchy.getLoggerFor("dynamic.access.formatter");

        logger.info("dynamic access formatter message");

        String formattedEvent = outputStream.toString();
        assertTrue(formattedEvent.startsWith("method="));
        assertTrue(formattedEvent.contains("message=dynamic access formatter message"));
    }
}
