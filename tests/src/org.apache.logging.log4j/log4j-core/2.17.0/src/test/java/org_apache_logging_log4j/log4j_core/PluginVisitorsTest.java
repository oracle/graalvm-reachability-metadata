/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.xml.XmlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PluginVisitorsTest {
    @Test
    void resolvesAttributeAndValueVisitorsForConfigurationProperties() throws Exception {
        try (LoggerContext context = new LoggerContext("plugin-visitors")) {
            XmlConfiguration configuration = TestSupport.configure(context, """
                    <Configuration status="OFF" shutdownHook="disable">
                      <Properties><Property name="region">west</Property></Properties>
                      <Loggers><Root level="OFF"/></Loggers>
                    </Configuration>
                    """);
            assertEquals("west", configuration.getStrSubstitutor().replace("${region}"));
        }
    }
}
