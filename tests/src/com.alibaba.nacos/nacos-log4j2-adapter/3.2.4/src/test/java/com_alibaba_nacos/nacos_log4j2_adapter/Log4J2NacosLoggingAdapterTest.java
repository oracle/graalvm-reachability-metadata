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
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import com.alibaba.nacos.common.logging.NacosLoggingAdapter;
import com.alibaba.nacos.common.logging.NacosLoggingProperties;
import com.alibaba.nacos.logger.adapter.log4j2.Log4j2NacosLoggingAdapterBuilder;
import com.alibaba.nacos.logger.adapter.log4j2.Log4j2NacosLoggingPropertiesHolder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.DefaultConfiguration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

@ResourceLock("nacos-log4j2-properties")
@Timeout(120)
public class Log4J2NacosLoggingAdapterTest {
    @TempDir
    Path directory;

    @Test
    void builderLoadsBundledConfigurationAndWritesEachNacosLog() throws Exception {
        NacosLoggingAdapter adapter = new Log4j2NacosLoggingAdapterBuilder().build();
        Properties values = new Properties();
        values.setProperty("JM.LOG.PATH", directory.toString());
        values.setProperty("com.alibaba.nacos.config.log.level", "DEBUG");
        values.setProperty("com.alibaba.nacos.naming.log.level", "DEBUG");
        values.setProperty("com.alibaba.nacos.ai.log.level", "DEBUG");
        NacosLoggingProperties properties = new NacosLoggingProperties(adapter.getDefaultConfigLocation(), values);
        NacosLoggingProperties previousProperties = Log4j2NacosLoggingPropertiesHolder.getProperties();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration previousConfiguration = context.getConfiguration();
        context.setConfiguration(new DefaultConfiguration());
        try {
            Configuration applicationConfiguration = context.getConfiguration();
            LoggerConfig applicationRoot = applicationConfiguration.getRootLogger();
            assertThat(adapter.isNeedReloadConfiguration()).isTrue();

            adapter.loadConfiguration(properties);

            assertThat(context.getConfiguration()).isSameAs(applicationConfiguration);
            assertThat(context.getConfiguration().getRootLogger()).isSameAs(applicationRoot);
            assertThat(adapter.isNeedReloadConfiguration()).isFalse();
            assertThat(Log4j2NacosLoggingPropertiesHolder.getProperties()).isSameAs(properties);
            Appender namingAppender = applicationConfiguration.getAppender("ASYNC_NAMING");
            assertThat(namingAppender.isStarted()).isTrue();

            context.getLogger("com.alibaba.nacos.client.config").debug("configuration payload");
            context.getLogger("com.alibaba.nacos.client.naming").debug("naming payload");
            context.getLogger("com.alibaba.nacos.common.remote.client").debug("remote payload");
            context.getLogger("com.alibaba.nacos.client.ai").debug("ai payload");
            context.getLogger("com.alibaba.nacos.client.naming").trace("filtered payload");

            adapter.loadConfiguration(properties);
            assertThat(applicationConfiguration.<Appender>getAppender("ASYNC_NAMING")).isSameAs(namingAppender);
        } finally {
            try {
                assertThat(context.stop(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                context.start(previousConfiguration);
                Log4j2NacosLoggingPropertiesHolder.setProperties(previousProperties);
            }
        }

        Path logs = directory.resolve("nacos");
        assertThat(Files.readString(logs.resolve("config.log"))).contains("configuration payload");
        assertThat(Files.readString(logs.resolve("naming.log")))
                .contains("naming payload").doesNotContain("filtered payload");
        assertThat(Files.readString(logs.resolve("remote.log"))).contains("remote payload");
        assertThat(Files.readString(logs.resolve("ai.log"))).contains("ai payload");
    }

    @Test
    void disabledDefaultConfigurationLeavesApplicationLoggingUnchanged() {
        NacosLoggingAdapter adapter = new Log4j2NacosLoggingAdapterBuilder().build();
        Properties values = new Properties();
        values.setProperty("nacos.logging.default.config.enabled", "false");
        NacosLoggingProperties properties = new NacosLoggingProperties(adapter.getDefaultConfigLocation(), values);
        NacosLoggingProperties previousProperties = Log4j2NacosLoggingPropertiesHolder.getProperties();
        LoggerContext context = (LoggerContext) LogManager.getContext(false);
        Configuration configuration = context.getConfiguration();
        try {
            adapter.loadConfiguration(properties);

            assertThat(context.getConfiguration()).isSameAs(configuration);
            assertThat(Log4j2NacosLoggingPropertiesHolder.getProperties()).isSameAs(properties);
            assertThat(Log4j2NacosLoggingPropertiesHolder.getValue("nacos.logging.default.config.enabled"))
                    .isEqualTo("false");
        } finally {
            Log4j2NacosLoggingPropertiesHolder.setProperties(previousProperties);
        }
    }
}
