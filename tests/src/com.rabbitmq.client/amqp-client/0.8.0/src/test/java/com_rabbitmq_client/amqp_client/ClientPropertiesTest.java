/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_rabbitmq_client.amqp_client;

import static org.assertj.core.api.Assertions.assertThat;

import com.rabbitmq.client.amqp.Connection;
import com.rabbitmq.client.amqp.ConnectionBuilder;
import com.rabbitmq.client.amqp.Environment;
import com.rabbitmq.client.amqp.impl.AmqpEnvironmentBuilder;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ClientPropertiesTest {

    private static final String CONTAINER_NAME = "graalvm-rabbitmq-amqp-client-test";
    private static final String DEFAULT_URI = "amqp://guest:guest@localhost:5672/%2f";

    private Environment environment;
    private Process rabbitMq;

    @BeforeAll
    void startRabbitMq() throws Exception {
        removeExistingContainer();
        environment = new AmqpEnvironmentBuilder().build();
        rabbitMq = new ProcessBuilder(
                "docker",
                "run",
                "--rm",
                "--name",
                CONTAINER_NAME,
                "-p",
                "5672:5672",
                "rabbitmq:4.1",
                "sh",
                "-c",
                "rabbitmq-plugins enable --offline rabbitmq_amqp1_0 && rabbitmq-server")
                .inheritIO()
                .start();

        waitUntil(() -> {
            if (!rabbitMq.isAlive()) {
                throw new IllegalStateException(
                        "RabbitMQ container exited before accepting AMQP connections");
            }
            try (Connection connection = openConnection("client-properties-readiness")) {
                return connection != null;
            }
        }, Duration.ofSeconds(120), Duration.ofSeconds(1));
    }

    @AfterAll
    void stopRabbitMq() throws Exception {
        if (environment != null) {
            environment.close();
        }
        if (rabbitMq != null && rabbitMq.isAlive()) {
            rabbitMq.destroy();
            if (!rabbitMq.waitFor(10, TimeUnit.SECONDS)) {
                rabbitMq.destroyForcibly();
                rabbitMq.waitFor(10, TimeUnit.SECONDS);
            }
        }
        removeExistingContainer();
    }

    @Test
    void opensConnectionWithDefaultClientProperties() {
        try (Connection connection = openConnection("client-properties-test")) {
            assertThat(connection).isNotNull();
        }
    }

    private Connection openConnection(String name) {
        ConnectionBuilder builder = environment.connectionBuilder()
                .uri(DEFAULT_URI)
                .name(name)
                .idleTimeout(Duration.ofSeconds(10));
        builder.recovery().activated(false);
        return builder.build();
    }

    private static void removeExistingContainer() throws Exception {
        Process process = new ProcessBuilder("docker", "rm", "-f", CONTAINER_NAME)
                .redirectErrorStream(true)
                .start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            process.waitFor(10, TimeUnit.SECONDS);
        }
    }

    private static void waitUntil(
            Callable<Boolean> condition, Duration timeout, Duration sleepTime) throws Exception {
        Exception lastException = null;
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            try {
                if (condition.call()) {
                    return;
                }
            } catch (Exception e) {
                lastException = e;
            }
            sleep(sleepTime);
        }
        String errorMessage = "Condition was not fulfilled within " + timeout;
        throw lastException == null
                ? new IllegalStateException(errorMessage)
                : new IllegalStateException(errorMessage, lastException);
    }

    private static void sleep(Duration sleepTime) {
        try {
            Thread.sleep(sleepTime.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for RabbitMQ", e);
        }
    }
}
