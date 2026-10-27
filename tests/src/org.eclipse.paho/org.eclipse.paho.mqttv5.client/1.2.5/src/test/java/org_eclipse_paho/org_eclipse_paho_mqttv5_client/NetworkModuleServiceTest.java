/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_paho.org_eclipse_paho_mqttv5_client;

import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.internal.NetworkModule;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class NetworkModuleServiceTest {
    @Test
    void createsANetworkModuleForAnRfc3986HostName() throws Exception {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        TestClient client = new TestClient(executor);

        try {
            assertThat(client.resolveServerUri("tcp://mqtt_broker:1883"))
                    .isEqualTo("tcp://mqtt_broker:1883");
        } finally {
            try {
                client.close();
            } finally {
                executor.shutdownNow();
            }
        }

        assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
    }

    private static final class TestClient extends MqttAsyncClient {
        private TestClient(ScheduledExecutorService executor) throws MqttException {
            super("tcp://localhost:1883", "network-module-test", new MemoryPersistence(), null, executor);
        }

        private String resolveServerUri(String serverUri) throws MqttException {
            MqttConnectionOptions options = new MqttConnectionOptions();
            NetworkModule[] modules = createNetworkModules(serverUri, options);
            return modules[0].getServerURI();
        }
    }
}
