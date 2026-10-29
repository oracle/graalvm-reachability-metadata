/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.logback_adapter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggerContextListener;
import ch.qos.logback.core.read.ListAppender;
import com.alibaba.nacos.common.logging.NacosLoggingAdapter;
import com.alibaba.nacos.common.logging.NacosLoggingProperties;
import com.alibaba.nacos.logger.adapter.logback14.LogbackNacosLoggingAdapterBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
@ResourceLock("logback-context")
public class LogbackNacosLoggingAdapterTest {
    @TempDir
    Path tempDirectory;

    @Test
    void loadsClientConfigurationAndEmitsOnlyEnabledMessages() throws Exception {
        Path configuration = tempDirectory.resolve("client-logging.xml");
        Files.writeString(configuration, """
                <configuration>
                    <nacosClientProperty name="clientLevel" source="client.log.level" defaultValue="WARN"/>
                    <logger name="com.alibaba.nacos.client.coverage" level="${clientLevel}" additivity="false"/>
                </configuration>
                """);
        Properties values = new Properties();
        values.setProperty("client.log.level", "DEBUG");
        NacosLoggingProperties properties = new NacosLoggingProperties(configuration.toUri().toString(), values);
        NacosLoggingAdapter adapter = new LogbackNacosLoggingAdapterBuilder().build();
        LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
        context.reset();
        try {
            Logger logger = context.getLogger("com.alibaba.nacos.client.coverage");
            assertThat(adapter.isAdaptedLogger(logger.getClass())).isTrue();
            assertThat(adapter.isAdaptedLogger(String.class)).isFalse();
            assertThat(adapter.isNeedReloadConfiguration()).isFalse();
            assertThat(adapter.getDefaultConfigLocation()).isEqualTo("classpath:nacos-logback14.xml");

            adapter.loadConfiguration(properties);
            assertThat(logger.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(logger.isAdditive()).isFalse();
            assertThat(context.getTurboFilterList()).hasSize(1);
            assertThat(context.getCopyOfListenerList()).hasSize(1);

            ListAppender<ILoggingEvent> appender = new ListAppender<>();
            appender.setContext(context);
            appender.start();
            logger.addAppender(appender);
            logger.trace("hidden trace");
            logger.debug("client {} connected", "alpha");
            logger.warn("client retry");
            assertThat(appender.list).extracting(ILoggingEvent::getFormattedMessage)
                    .containsExactly("client alpha connected", "client retry");

            adapter.loadConfiguration(properties);
            assertThat(context.getTurboFilterList()).hasSize(1);
            assertThat(context.getCopyOfListenerList()).hasSize(1);

            context.reset();
            assertThat(logger.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(logger.isAdditive()).isFalse();
            assertThat(context.getCopyOfListenerList()).hasSize(1);
        } finally {
            for (LoggerContextListener listener : context.getCopyOfListenerList()) {
                context.removeListener(listener);
            }
            context.reset();
        }
    }
}
