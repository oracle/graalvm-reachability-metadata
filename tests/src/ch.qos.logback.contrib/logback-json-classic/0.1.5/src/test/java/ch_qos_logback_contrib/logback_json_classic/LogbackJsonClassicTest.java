/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ch_qos_logback_contrib.logback_json_classic;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.assertj.core.api.Assertions.assertThat;

class LogbackJsonClassicTest {

    private static final String CONFIG_TAG = """
            <configuration>
                <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
                    <encoder class="ch.qos.logback.core.encoder.LayoutWrappingEncoder">
                        <layout class="ch.qos.logback.contrib.json.classic.JsonLayout">
                            <includeTimestamp>true</includeTimestamp>
                            <timestampFormat>yyyy-MM-dd HH:mm:ss.SSS</timestampFormat>
                            <timestampFormatTimezoneId>UTC</timestampFormatTimezoneId>
                            <appendLineSeparator>false</appendLineSeparator>
                            <jsonFormatter class="ch_qos_logback_contrib.logback_json_classic.CustomJsonFormatter"/>
                            <includeLevel>true</includeLevel>
                            <includeThreadName>true</includeThreadName>
                            <includeMDC>true</includeMDC>
                            <includeLoggerName>true</includeLoggerName>
                            <includeFormattedMessage>true</includeFormattedMessage>
                            <includeMessage>true</includeMessage>
                            <includeException>true</includeException>
                            <includeContextName>true</includeContextName>
                        </layout>
                    </encoder>
                </appender>
                <root>
                    <appender-ref ref="STDOUT"/>
                </root>
            </configuration>
            """;

    private final PrintStream systemOut = System.out;

    private ByteArrayOutputStream outputStreamCaptor;

    @BeforeEach
    public void setUp() {
        outputStreamCaptor = new ByteArrayOutputStream();
        System.setOut(new PrintStream(outputStreamCaptor));
    }

    @AfterEach
    public void tearDown() {
        System.setOut(systemOut);
    }

    @Test
    void test() throws Exception {
        LoggerContext testLoggerContext = new LoggerContext();

        JoranConfigurator joranConfigurator = new JoranConfigurator();
        joranConfigurator.setContext(testLoggerContext);

        joranConfigurator.doConfigure(new ByteArrayInputStream(CONFIG_TAG.getBytes()));

        Logger testLogger = testLoggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
        testLogger.info("test info message");

        String loggedMessage = outputStreamCaptor.toString();
        assertThat(loggedMessage).contains("message=test info message");
    }
}
