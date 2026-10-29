/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_logback_adapter_12;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.nacos.logger.adapter.logback12.NacosLogbackConfiguratorAdapterV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

public class NacosLogbackConfiguratorAdapterV1Test {
    @TempDir
    Path tempDirectory;

    @Test
    void configuresLoggingLevelsFromUrl() throws Exception {
        Path configuration = tempDirectory.resolve("nacos-logging.xml");
        Files.writeString(configuration, """
                <configuration>
                    <logger name="example.nacos.client" level="DEBUG" additivity="false"/>
                    <root level="ERROR"/>
                </configuration>
                """);

        LoggerContext context = new LoggerContext();
        try {
            context.start();
            NacosLogbackConfiguratorAdapterV1 configurator = new NacosLogbackConfiguratorAdapterV1();
            configurator.setContext(context);
            configurator.configure(configuration.toUri().toURL());

            Logger logger = context.getLogger("example.nacos.client");
            assertThat(logger.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(logger.isAdditive()).isFalse();
            assertThat(context.getLogger(Logger.ROOT_LOGGER_NAME).getLevel()).isEqualTo(Level.ERROR);

            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.setContext(context);
            appender.start();
            logger.addAppender(appender);

            logger.trace("filtered trace");
            logger.debug("configured debug");
            logger.error("configured error");

            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .containsExactly("configured debug", "configured error");
        } finally {
            context.stop();
        }
    }
}
