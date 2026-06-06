/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_1_2_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.apache.log4j.Appender;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PropertyConfigurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class BuilderManagerTest {

    private static final String APPENDER_NAME = "builderManagerConsole";
    private final PrintStream originalErr = System.err;

    @AfterEach
    void resetLogging() {
        LogManager.resetConfiguration();
        System.setErr(originalErr);
    }

    @Test
    void configuresConsoleAppenderAndPatternLayoutFromProperties() {
        ByteArrayOutputStream loggingOutput = new ByteArrayOutputStream();
        System.setErr(new PrintStream(loggingOutput, true, StandardCharsets.UTF_8));

        PropertyConfigurator.configure(consoleAppenderProperties());

        Logger rootLogger = Logger.getRootLogger();
        Appender appender = rootLogger.getAppender(APPENDER_NAME);
        assertThat(appender).isNotNull();
        assertThat(appender.getLayout()).isNotNull();

        rootLogger.info("builder manager message");

        assertThat(loggingOutput.toString(StandardCharsets.UTF_8))
                .contains("BUILDER INFO|builder manager message");
    }

    private static Properties consoleAppenderProperties() {
        Properties properties = new Properties();
        properties.setProperty("log4j.rootLogger", "INFO, " + APPENDER_NAME);
        properties.setProperty("log4j.appender." + APPENDER_NAME, "org.apache.log4j.ConsoleAppender");
        properties.setProperty("log4j.appender." + APPENDER_NAME + ".Target", "System.err");
        properties.setProperty("log4j.appender." + APPENDER_NAME + ".ImmediateFlush", "true");
        properties.setProperty("log4j.appender." + APPENDER_NAME + ".layout", "org.apache.log4j.PatternLayout");
        properties.setProperty("log4j.appender." + APPENDER_NAME + ".layout.ConversionPattern", "BUILDER %p|%m%n");
        return properties;
    }
}
