/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

public class DefaultConfigurationBuilderTest {
    @Test
    void buildsAndStartsAProgrammaticLoggingConfiguration() {
        try (LoggerContext context = new LoggerContext("configuration-builder")) {
            ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
            builder.setLoggerContext(context);
            builder.setConfigurationName("application");
            builder.setShutdownHook("disable");
            builder.add(builder.newAppender("output", "Console")
                    .add(builder.newLayout("PatternLayout").addAttribute("pattern", "%logger:%message")));
            builder.add(builder.newRootLogger(Level.WARN).add(builder.newAppenderRef("output")));
            BuiltConfiguration configuration = builder.build();
            context.start(configuration);
            assertEquals("application", configuration.getName());
            assertEquals(Level.WARN, context.getLogger("worker").getLevel());
            PatternLayout layout = assertInstanceOf(PatternLayout.class, configuration.getAppender("output").getLayout());
            assertEquals("application.worker:hello", layout.toSerializable(TestSupport.event("hello")));
        }
    }
}
