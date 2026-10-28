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
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.apache.logging.log4j.simple.SimpleLogger;
import org.apache.logging.log4j.util.PropertiesUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleLoggerTest {

    @AfterEach
    void clearContext() {
        ThreadContext.clearAll();
    }

    @Test
    void formatsParameterizedMessagesWithContextMap() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Properties properties = new Properties();
        properties.setProperty("org.apache.logging.log4j.simplelog.com.example.orders.level", "debug");
        SimpleLogger logger = new SimpleLogger("com.example.orders", Level.INFO, true, true, false, true, null, null,
                new PropertiesUtil(properties), new PrintStream(output, true, StandardCharsets.UTF_8));
        ThreadContext.put("user", "ada");

        logger.debug("Order {} shipped to {}", 17, "Berlin");
        logger.trace("not printed");

        assertThat(logger.getMessageFactory()).isInstanceOf(ParameterizedMessageFactory.class);
        assertThat(output.toString(StandardCharsets.UTF_8).trim())
                .isEqualTo("DEBUG orders Order 17 shipped to Berlin {user=ada}");
    }

    @Test
    void appendsStackTraceOfTrailingThrowableParameter() {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SimpleLogger logger = new SimpleLogger("payments", Level.WARN, true, false, false, false, null, null,
                new PropertiesUtil(new Properties()), new PrintStream(output, true, StandardCharsets.UTF_8));

        logger.info("ignored");
        logger.error("Charge {} failed", "c-1", new IllegalStateException("card declined"));

        String text = output.toString(StandardCharsets.UTF_8);
        assertThat(text).startsWith("ERROR payments Charge c-1 failed java.lang.IllegalStateException: card declined");
        assertThat(text).doesNotContain("ignored");
    }
}
