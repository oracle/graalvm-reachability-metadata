/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.ConsoleAppender;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class PluginBuilderTest {
    @Test
    void injectsLayoutBuilderFieldsAndInvokesFilterFactories() throws Exception {
        try (LoggerContext context = new LoggerContext("plugin-builder")) {
            XmlConfiguration configuration = TestSupport.configure(context, """
                    <Configuration status="OFF" shutdownHook="disable">
                      <Appenders><Console name="output">
                        <PatternLayout pattern="%level:%message"/>
                        <ThresholdFilter level="WARN" onMatch="ACCEPT" onMismatch="DENY"/>
                      </Console></Appenders>
                      <Loggers><Root level="INFO"><AppenderRef ref="output"/></Root></Loggers>
                    </Configuration>
                    """);
            ConsoleAppender appender = assertInstanceOf(ConsoleAppender.class, configuration.getAppender("output"));
            PatternLayout layout = assertInstanceOf(PatternLayout.class, appender.getLayout());
            assertEquals("INFO:hello", layout.toSerializable(TestSupport.event("hello")));
            assertEquals(Filter.Result.DENY, appender.getFilter().filter(TestSupport.event("hello")));
        }
    }
}
