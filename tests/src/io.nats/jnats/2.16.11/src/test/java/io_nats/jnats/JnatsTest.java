/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_nats.jnats;

import io.nats.client.Connection;
import io.nats.client.Dispatcher;
import io.nats.client.Nats;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JnatsTest {

    private Process process;

    @BeforeAll
    public void init() throws IOException {
        System.out.println("Starting NATS ...");
        process = new ProcessBuilder("docker", "run", "--rm", "-p", "4222:4222", "nats:2.12.4").inheritIO().start();

        waitUntil(() -> {
            openConnection().close();
            return true;
        }, 120, 1);

        System.out.println("NATS started");
    }

    @AfterAll
    public void close() {
        if (process != null && process.isAlive()) {
            System.out.println("Shutting down NATS");
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

    private Connection openConnection() throws Exception {
        return Nats.connect();
    }

    @Test
    void test() throws Exception {
        try (Connection connection = openConnection()) {
            final List<String> receivedMessages = new ArrayList<>();
            Dispatcher dispatcher = connection.createDispatcher((msg) -> receivedMessages.add(new String(msg.getData(), StandardCharsets.UTF_8)));
            dispatcher.subscribe("test-subject");

            connection.publish("test-subject", "test-message".getBytes(StandardCharsets.UTF_8));

            waitUntil(() -> !receivedMessages.isEmpty(), 10, 1);

            assertThat(receivedMessages)
                    .hasSize(1)
                    .containsExactly("test-message");
        }
    }
}
