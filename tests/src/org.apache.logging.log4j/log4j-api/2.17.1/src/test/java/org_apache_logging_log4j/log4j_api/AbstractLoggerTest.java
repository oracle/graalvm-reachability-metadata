/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_api;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.message.EntryMessage;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.apache.logging.log4j.simple.SimpleLogger;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractLoggerTest {

    @Test
    void createsDefaultFlowMessagesThroughLoggerTracing() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            SimpleLogger logger = new SimpleLogger("checkout", Level.TRACE, true, false, false, false, null, null,
                    new PropertiesUtil(new Properties()), output);

            EntryMessage entry = logger.traceEntry("order {}", "o-42");
            String result = logger.traceExit(entry, "accepted");

            assertThat(entry.getFormattedMessage()).isEqualTo("Enter order o-42");
            assertThat(result).isEqualTo("accepted");
            assertThat(bytes.toString(StandardCharsets.UTF_8)).isEqualTo(
                    "TRACE checkout Enter order o-42" + System.lineSeparator()
                            + "TRACE checkout Exit order o-42: accepted" + System.lineSeparator());
        }
    }

    @Test
    void retainsLoggerConfigurationAndFlowFactoryAfterSerialization() throws Exception {
        SimpleLogger original = new SimpleLogger("serialized-checkout", Level.TRACE, true, false, false, false, null,
                ParameterizedMessageFactory.INSTANCE, new PropertiesUtil(new Properties()), null);

        SimpleLogger copy = SerializationSupport.roundTrip(original, SimpleLogger.class);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            copy.setStream(output);
            copy.info("Order {} restored", "o-43");
            EntryMessage entry = copy.traceEntry("order {}", "o-43");
            copy.traceExit(entry);

            assertThat(copy.getName()).isEqualTo("serialized-checkout");
            assertThat(copy.getLevel()).isEqualTo(Level.TRACE);
            assertThat(bytes.toString(StandardCharsets.UTF_8)).isEqualTo(
                    "INFO serialized-checkout Order o-43 restored" + System.lineSeparator()
                            + "TRACE serialized-checkout Enter order o-43" + System.lineSeparator()
                            + "TRACE serialized-checkout Exit order o-43" + System.lineSeparator());
        }
    }
}
