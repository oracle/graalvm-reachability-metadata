/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;

import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.layout.SerializedLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class SerializedLayoutTest {
    @Test
    void producesAStreamOfLogEventsWithASingleHeader() throws Exception {
        SerializedLayout layout = SerializedLayout.createLayout();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bytes.write(layout.getHeader());
        bytes.write(layout.toByteArray(TestSupport.event("first")));
        bytes.write(layout.toByteArray(TestSupport.event("second")));
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            LogEvent first = assertInstanceOf(LogEvent.class, input.readObject());
            LogEvent second = assertInstanceOf(LogEvent.class, input.readObject());
            assertEquals("first", first.getMessage().getFormattedMessage());
            assertEquals("second", second.getMessage().getFormattedMessage());
            assertEquals("application.worker", first.getLoggerName());
            assertEquals(first.getLevel(), second.getLevel());
        }
    }
}
