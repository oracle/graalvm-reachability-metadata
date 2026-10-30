/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba_nacos.nacos_log4j2_adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import com.alibaba.nacos.logger.adapter.log4j2.NacosLog4j2Configurator;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilder;
import org.apache.logging.log4j.core.config.builder.api.ConfigurationBuilderFactory;
import org.apache.logging.log4j.core.config.builder.impl.BuiltConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

@Timeout(120)
public class NacosLog4j2ConfiguratorTest {
    @TempDir
    Path directory;

    @Test
    void mergesNacosLoggersAndAppendersWithoutReplacingApplicationLoggers() throws Exception {
        Path output = directory.resolve("merged.log");
        Path xml = directory.resolve("nacos.xml");
        String outputPath = output.toString().replace("&", "&amp;").replace("\"", "&quot;");
        Files.writeString(xml, """
                <Configuration status="WARN">
                    <Appenders>
                        <File name="NACOS_FILE" fileName="%s" immediateFlush="true">
                            <PatternLayout pattern="%%level:%%msg%%n"/>
                        </File>
                    </Appenders>
                    <Loggers>
                        <Logger name="com.alibaba.nacos.feature" level="DEBUG" additivity="false">
                            <AppenderRef ref="NACOS_FILE"/>
                        </Logger>
                        <Logger name="example.unrelated" level="TRACE"/>
                        <Root level="TRACE"/>
                    </Loggers>
                </Configuration>
                """.formatted(outputPath));

        ConfigurationBuilder<BuiltConfiguration> builder = ConfigurationBuilderFactory.newConfigurationBuilder();
        builder.add(builder.newRootLogger(Level.ERROR));
        builder.add(builder.newLogger("example.application", Level.WARN));
        LoggerContext context = new LoggerContext("nacos-additive-configuration");
        context.start(builder.build());
        try {
            Configuration applicationConfiguration = context.getConfiguration();
            LoggerConfig applicationLogger = applicationConfiguration.getLoggerConfig("example.application");
            LoggerConfig applicationRoot = applicationConfiguration.getRootLogger();

            new NacosLog4j2Configurator().configure(context, xml.toUri());

            assertThat(context.getConfiguration()).isSameAs(applicationConfiguration);
            assertThat(applicationConfiguration.getRootLogger()).isSameAs(applicationRoot);
            assertThat(applicationRoot.getLevel()).isEqualTo(Level.ERROR);
            assertThat(applicationConfiguration.getLoggerConfig("example.application")).isSameAs(applicationLogger);
            assertThat(applicationLogger.getLevel()).isEqualTo(Level.WARN);
            assertThat(applicationConfiguration.getLoggers()).containsKey("com.alibaba.nacos.feature")
                    .doesNotContainKey("example.unrelated");
            assertThat(applicationConfiguration.getAppender("NACOS_FILE").isStarted()).isTrue();

            context.getLogger("com.alibaba.nacos.feature").debug("merged payload");
            context.getLogger("com.alibaba.nacos.feature").trace("filtered payload");
            assertThat(Files.readString(output)).isEqualTo("DEBUG:merged payload" + System.lineSeparator());
        } finally {
            assertThat(context.stop(10, TimeUnit.SECONDS)).isTrue();
        }
    }
}
