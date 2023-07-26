/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_paho.org_eclipse_paho_client_mqttv3;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Mqttv3Test {

    private static final String BROKER = "tcp://localhost:1883";

    private static final String CLIENT_ID = MqttAsyncClient.generateClientId();

    private static final String TOPIC = "Metadata Topic";

    private final MemoryPersistence persistence = new MemoryPersistence();

    private Process process;

    private MqttClient client;

    @BeforeAll
    void beforeAll() throws IOException, MqttException {
        System.out.println("Starting MQTT broker ...");
        process = new ProcessBuilder("docker", "run", "--rm", "-p", "1883:1883", "eclipse-mosquitto:2.0.15", "mosquitto", "-c", "/mosquitto-no-auth.conf")
                .inheritIO()
                .start();

        client = new MqttClient(BROKER, CLIENT_ID, persistence);

        waitUntil(() -> {
            client.connect();
            return true;
        }, 120, 1);

        System.out.println("MQTT broker started");
    }

    @AfterAll
    public void close() throws MqttException {
        if (client != null) {
            client.disconnect();
            client.close();
        }
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down MQTT broker");
            process.destroy();
        }
    }

    // not using Awaitility library because of `com.oracle.svm.core.jdk.UnsupportedFeatureError: ThreadMXBean methods` issue
    // which happens if a condition is not fulfilled when a test is running in a native image
    private void waitUntil(Callable<Boolean> conditionEvaluator, int timeoutSeconds, int sleepTimeSeconds) {
        Exception lastException = null;

        long end  = System.currentTimeMillis() + timeoutSeconds * 1000L;
        while (System.currentTimeMillis() < end) {
            try {
                Thread.sleep(sleepTimeSeconds * 1000L);
            } catch (InterruptedException e) {
                // continue
            }
            try {
                if (conditionEvaluator.call()) {
                    return;
                }
            } catch (Exception e) {
                lastException = e;
            }
        }
        String errorMessage = "Condition was not fulfilled within " + timeoutSeconds + " seconds";
        throw lastException == null ? new IllegalStateException(errorMessage) : new IllegalStateException(errorMessage, lastException);
    }

    @Test
    void test() throws Exception {
        final List<String> receivedMessages = new ArrayList<>();
        client.setCallback(new MqttCallback() {

            public void messageArrived(String topic, MqttMessage message) throws Exception {
                receivedMessages.add(new String(message.getPayload()));
            }

            public void connectionLost(Throwable cause) {
                receivedMessages.add("Connection to MQTT broker lost: " + cause.getMessage());
            }

            public void deliveryComplete(IMqttDeliveryToken token) {
            }
        });
        client.subscribe(TOPIC, 0);

        MqttMessage message = new MqttMessage("test-message".getBytes());
        message.setQos(0);
        client.publish(TOPIC, message);

        waitUntil(() -> !receivedMessages.isEmpty(), 10, 1);

        assertThat(receivedMessages)
                .hasSize(1)
                .containsExactly("test-message");
    }
}
