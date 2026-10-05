/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_paho.org_eclipse_paho_mqttv5_client;

import org.eclipse.paho.mqttv5.client.logging.LoggerFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.logging.LogManager;

import static org.assertj.core.api.Assertions.assertThat;

public class LoggerFactoryTest {
    @Test
    void readsAConfiguredJavaLoggingProperty() throws Exception {
        byte[] configuration = "paho.test.logging=enabled\n".getBytes(StandardCharsets.UTF_8);
        LogManager logManager = LogManager.getLogManager();
        try {
            logManager.readConfiguration(new ByteArrayInputStream(configuration));
            assertThat(LoggerFactory.getLoggingProperty("paho.test.logging")).isEqualTo("enabled");
        } finally {
            logManager.readConfiguration();
        }
    }
}
