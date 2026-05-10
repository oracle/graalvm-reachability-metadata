/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_1_2_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Properties;

import org.apache.log4j.Layout;
import org.apache.log4j.builders.BuilderManager;
import org.apache.log4j.builders.layout.LayoutBuilder;
import org.apache.log4j.config.PropertiesConfiguration;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.Test;

public class BuilderManagerTest {

    @Test
    void createsAbstractBuilderPluginWithPropertiesConstructor() {
        Properties properties = new Properties();
        properties.setProperty("log4j.appender.console.layout.ConversionPattern", "%m%n");
        LoggerContext loggerContext = new LoggerContext("BuilderManagerTest");

        try {
            PropertiesConfiguration configuration = new PropertiesConfiguration(loggerContext, properties);
            BuilderManager manager = new BuilderManager();

            Layout layout = manager.<LayoutBuilder, Layout>parse(
                    "org.apache.log4j.PatternLayout",
                    "log4j.appender.console.layout",
                    properties,
                    configuration,
                    BuilderManager.INVALID_LAYOUT);

            assertThat(layout).isNotNull();
            assertThat(layout).isNotSameAs(BuilderManager.INVALID_LAYOUT);
            assertThat(layout.getContentType()).isEqualTo("text/plain");
        } finally {
            loggerContext.stop();
        }
    }
}
