/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.ansi.AnsiOutput;
import org.springframework.boot.ansi.AnsiOutput.Enabled;
import org.springframework.boot.logging.LoggingInitializationContext;
import org.springframework.boot.logging.logback.LogbackLoggingSystem;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

public class LogbackConfigurationTest {
    @TempDir
    Path directory;

    @Test
    void loadsBootPatternConvertersAndWritesColoredContextAndExceptionDetails() throws Exception {
        Path output = this.directory.resolve("orders.log");
        Path configuration = this.directory.resolve("logging.xml");
        Files.writeString(configuration, """
                <configuration>
                    <conversionRule conversionWord="clr"
                        class="org.springframework.boot.logging.logback.ColorConverter"/>
                    <conversionRule conversionWord="esb"
                        class="org.springframework.boot.logging.logback.EnclosedInSquareBracketsConverter"/>
                    <conversionRule conversionWord="wEx"
                        class="org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter"/>
                    <appender name="ORDERS" class="ch.qos.logback.core.FileAppender">
                        <file>%s</file>
                        <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
                            <pattern>%%clr(%%msg){red} %%esb(%%X{tenant})%%wEx%%n</pattern>
                        </encoder>
                    </appender>
                    <root level="INFO"><appender-ref ref="ORDERS"/></root>
                </configuration>
                """.formatted(output.toString().replace("&", "&amp;")));
        ConfigurableLogbackSystem system = new ConfigurableLogbackSystem(getClass().getClassLoader());
        Enabled previous = AnsiOutput.getEnabled();
        String previousTenant = MDC.get("tenant");
        try {
            AnsiOutput.setEnabled(Enabled.ALWAYS);
            system.beforeInitialize();
            system.configure(new LoggingInitializationContext(new StandardEnvironment()),
                    configuration.toUri().toString());
            MDC.put("tenant", "warehouse");
            LoggerFactory.getLogger("consumer.orders").error("dispatch failed", new IllegalStateException("queue full"));

            String log = Files.readString(output);
            assertThat(log).contains("\u001b[31mdispatch failed\u001b[0;39m", "[warehouse] ",
                    "java.lang.IllegalStateException: queue full", "LogbackConfigurationTest");
        } finally {
            system.getShutdownHandler().run();
            system.cleanUp();
            AnsiOutput.setEnabled(previous);
            if (previousTenant == null) {
                MDC.remove("tenant");
            } else {
                MDC.put("tenant", previousTenant);
            }
        }
    }

    public static class ConfigurableLogbackSystem extends LogbackLoggingSystem {
        public ConfigurableLogbackSystem(ClassLoader classLoader) {
            super(classLoader);
        }

        public void configure(LoggingInitializationContext context, String location) {
            super.loadConfiguration(context, location, null);
        }
    }
}
