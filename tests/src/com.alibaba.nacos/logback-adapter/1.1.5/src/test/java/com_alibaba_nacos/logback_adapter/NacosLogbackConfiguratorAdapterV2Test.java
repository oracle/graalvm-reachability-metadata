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
import com.alibaba.nacos.common.logging.NacosLoggingProperties;
import com.alibaba.nacos.logger.adapter.logback14.NacosLogbackConfiguratorAdapterV2;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class NacosLogbackConfiguratorAdapterV2Test {
    @TempDir
    Path tempDirectory;

    @Test
    void resolvesClientPropertiesAndDefaultsInTheirConfiguredScopes() throws Exception {
        Path configuration = tempDirectory.resolve("properties.xml");
        Files.writeString(configuration, """
                <configuration>
                    <nacosClientProperty scope="context" name="clientDestination"
                        source="client.destination" defaultValue="fallback-destination"/>
                    <nacosClientProperty scope="context" name="missingValue"
                        source="missing.client.value" defaultValue="fallback-value"/>
                    <nacosClientProperty name="clientLevel" source="client.level" defaultValue="WARN"/>
                    <logger name="example.client" level="${clientLevel}" additivity="false"/>
                </configuration>
                """);
        Properties values = new Properties();
        values.setProperty("client.destination", "configured-destination");
        values.setProperty("client.level", "DEBUG");
        LoggerContext context = new LoggerContext();
        try {
            context.start();
            NacosLogbackConfiguratorAdapterV2 configurator = new NacosLogbackConfiguratorAdapterV2();
            configurator.setContext(context);
            configurator.setLoggingProperties(new NacosLoggingProperties(null, values));
            configurator.configure(configuration.toUri().toURL());

            assertThat(context.getProperty("clientDestination")).isEqualTo("configured-destination");
            assertThat(context.getProperty("missingValue")).isEqualTo("fallback-value");
            assertThat(context.getProperty("clientLevel")).isNull();
            Logger logger = context.getLogger("example.client");
            assertThat(logger.getLevel()).isEqualTo(Level.DEBUG);
            assertThat(logger.isAdditive()).isFalse();
        } finally {
            context.stop();
        }
    }

    @Test
    void usesXmlDefaultsWithoutClientProperties() throws Exception {
        Path configuration = tempDirectory.resolve("defaults.xml");
        Files.writeString(configuration, """
                <configuration>
                    <nacosClientProperty scope="context" name="clientDestination"
                        source="client.destination" defaultValue="local-destination"/>
                    <nacosClientProperty name="clientLevel" source="client.level" defaultValue="ERROR"/>
                    <logger name="example.defaults" level="${clientLevel}"/>
                </configuration>
                """);
        LoggerContext context = new LoggerContext();
        try {
            context.start();
            NacosLogbackConfiguratorAdapterV2 configurator = new NacosLogbackConfiguratorAdapterV2();
            configurator.setContext(context);
            configurator.configure(configuration.toUri().toURL());

            assertThat(context.getProperty("clientDestination")).isEqualTo("local-destination");
            assertThat(context.getLogger("example.defaults").getLevel()).isEqualTo(Level.ERROR);
        } finally {
            context.stop();
        }
    }
}
