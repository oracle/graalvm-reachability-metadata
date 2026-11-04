/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_logmanager.jboss_logmanager;

import org.jboss.logmanager.LogContext;
import org.jboss.logmanager.Logger;
import org.jboss.logmanager.filters.LevelRangeFilter;
import org.jboss.logmanager.formatters.PatternFormatter;
import org.jboss.logmanager.handlers.OutputStreamHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises level-range filtering for an application that routes only routine events to a handler.
public class LevelRangeFilteringTest {
    @Test
    @Timeout(60)
    public void routesOnlyEventsInsideTheConfiguredLevelRange() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        OutputStreamHandler handler = new OutputStreamHandler(output,
                new PatternFormatter("%p [%c] %s%n"));
        handler.setFilter(new LevelRangeFilter(Level.INFO, true, Level.WARNING, false));

        Logger logger = LogContext.create().getLogger("application.fulfillment");
        logger.setLevel(Level.ALL);
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);

        try {
            logger.fine("internal diagnostic");
            logger.info("shipment accepted");
            logger.warning("shipment delayed");
            logger.severe("shipment failed");

            assertEquals("INFO [application.fulfillment] shipment accepted"
                    + System.lineSeparator(), output.toString(StandardCharsets.UTF_8));
        } finally {
            logger.removeHandler(handler);
            handler.close();
        }
    }
}
