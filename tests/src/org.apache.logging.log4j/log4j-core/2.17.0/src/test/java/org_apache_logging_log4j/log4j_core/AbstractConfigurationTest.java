/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_logging_log4j.log4j_core;

import java.util.Map;

import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.net.Advertiser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class AbstractConfigurationTest {
    @Test
    void advertisesConfigurationAndWithdrawsItOnShutdown() throws Exception {
        RecordingAdvertiser.properties = null;
        RecordingAdvertiser.withdrawn = null;
        try (LoggerContext context = new LoggerContext("advertisement")) {
            TestSupport.configure(context, """
                    <Configuration status="OFF" advertiser="recording-advertiser" shutdownHook="disable">
                      <Loggers><Root level="OFF"/></Loggers>
                    </Configuration>
                    """);
            assertNotNull(RecordingAdvertiser.properties);
            assertEquals("configuration", RecordingAdvertiser.properties.get("name"));
            assertEquals("text/xml", RecordingAdvertiser.properties.get("contentType"));
        }
        assertSame(RecordingAdvertiser.properties, RecordingAdvertiser.withdrawn);
    }

    @Plugin(name = "recording-advertiser", category = "Core", elementType = "advertiser")
    public static class RecordingAdvertiser implements Advertiser {
        static Map<String, String> properties;
        static Object withdrawn;

        @Override
        public Object advertise(Map<String, String> values) {
            properties = Map.copyOf(values);
            return properties;
        }

        @Override
        public void unadvertise(Object advertisement) {
            withdrawn = advertisement;
        }
    }
}
