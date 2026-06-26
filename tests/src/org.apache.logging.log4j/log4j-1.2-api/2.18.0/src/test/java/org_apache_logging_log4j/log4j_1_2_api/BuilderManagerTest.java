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
import org.apache.log4j.bridge.LayoutWrapper;
import org.apache.log4j.builders.BuilderManager;
import org.apache.log4j.config.PropertiesConfiguration;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.Test;

public class BuilderManagerTest {

    @Test
    void parsesAbstractBuilderPluginWithPropertyConstructor() {
        Properties properties = new Properties();
        String layoutPrefix = "log4j.appender.console.layout";
        properties.setProperty(layoutPrefix + ".ConversionPattern", "%p %c - %m%n");

        LoggerContext loggerContext = new LoggerContext("builder-manager-test");
        try {
            PropertiesConfiguration configuration = new PropertiesConfiguration(loggerContext, properties);
            BuilderManager manager = new BuilderManager();

            Layout layout = manager.parse(
                    "org.apache.log4j.PatternLayout",
                    layoutPrefix,
                    properties,
                    configuration,
                    BuilderManager.INVALID_LAYOUT);

            assertThat(layout).isInstanceOf(LayoutWrapper.class);
            LayoutWrapper wrapper = (LayoutWrapper) layout;
            assertThat(wrapper.getLayout()).isInstanceOf(PatternLayout.class);
        } finally {
            loggerContext.close();
        }
    }
}
