/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micrometer.micrometer_registry_statsd;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.statsd.StatsdConfig;
import io.micrometer.statsd.StatsdMeterRegistry;
import io.micrometer.statsd.StatsdProtocol;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class StatsdMeterRegistryTransportTest {
    private static final int IO_TIMEOUT_MILLIS = 10_000;

    @Test
    void publishesCountersAndPolledGaugesOverUdp() throws Exception {
        try (DatagramSocket server = new DatagramSocket(0)) {
            server.setSoTimeout(IO_TIMEOUT_MILLIS);
            AtomicReference<String> received = new AtomicReference<>("");
            AtomicReference<Throwable> receiverFailure = new AtomicReference<>();
            CountDownLatch expectedMetricsReceived = new CountDownLatch(1);
            Thread receiver = new Thread(
                    () -> receiveUdpMetrics(server, received, receiverFailure, expectedMetricsReceived),
                    "statsd-udp-receiver");
            receiver.start();

            LocalStatsdConfig config = new LocalStatsdConfig(server.getLocalPort(), StatsdProtocol.UDP);
            StatsdMeterRegistry registry = new StatsdMeterRegistry(config, Clock.SYSTEM);
            try {
                AtomicInteger queueDepth = new AtomicInteger(7);
                registry.gauge("queue.depth", queueDepth);
                Counter counter = registry.counter("requests.processed", "status", "ok");

                publishUntilReceived(counter, expectedMetricsReceived);
            } finally {
                registry.close();
            }

            receiver.join(IO_TIMEOUT_MILLIS);
            assertThat(receiver.isAlive()).isFalse();
            assertThat(receiverFailure.get()).isNull();
            assertThat(received.get()).contains("requests.processed:", "|c", "queue.depth:", "|g");
        }
    }

    @Test
    void publishesCountersOverTcp() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            server.setSoTimeout(IO_TIMEOUT_MILLIS);
            AtomicReference<String> received = new AtomicReference<>("");
            AtomicReference<Throwable> receiverFailure = new AtomicReference<>();
            CountDownLatch expectedMetricReceived = new CountDownLatch(1);
            Thread receiver = new Thread(
                    () -> receiveTcpMetric(server, received, receiverFailure, expectedMetricReceived),
                    "statsd-tcp-receiver");
            receiver.start();

            LocalStatsdConfig config = new LocalStatsdConfig(server.getLocalPort(), StatsdProtocol.TCP);
            StatsdMeterRegistry registry = StatsdMeterRegistry.builder(config).build();
            try {
                Counter counter = registry.counter("jobs.completed", "result", "success");

                publishUntilReceived(counter, expectedMetricReceived);
            } finally {
                registry.close();
            }

            receiver.join(IO_TIMEOUT_MILLIS);
            assertThat(receiver.isAlive()).isFalse();
            assertThat(receiverFailure.get()).isNull();
            assertThat(received.get()).contains("jobs.completed:", "|c", "result:success");
        }
    }

    private static void publishUntilReceived(Counter counter, CountDownLatch received) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (received.getCount() != 0 && System.nanoTime() < deadline) {
            counter.increment();
            Thread.sleep(50);
        }
        assertThat(received.await(10, TimeUnit.SECONDS)).isTrue();
    }

    private static void receiveUdpMetrics(DatagramSocket server, AtomicReference<String> received,
            AtomicReference<Throwable> receiverFailure, CountDownLatch expectedMetricsReceived) {
        StringBuilder payloads = new StringBuilder();
        byte[] bytes = new byte[2048];
        try {
            while (!containsUdpMetrics(payloads)) {
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
                server.receive(packet);
                payloads.append(new String(packet.getData(), packet.getOffset(), packet.getLength(),
                        StandardCharsets.UTF_8));
            }
            received.set(payloads.toString());
        } catch (Throwable failure) {
            receiverFailure.set(failure);
        } finally {
            expectedMetricsReceived.countDown();
        }
    }

    private static boolean containsUdpMetrics(StringBuilder payloads) {
        return payloads.indexOf("requests.processed:") >= 0 && payloads.indexOf("queue.depth:") >= 0;
    }

    private static void receiveTcpMetric(ServerSocket server, AtomicReference<String> received,
            AtomicReference<Throwable> receiverFailure, CountDownLatch expectedMetricReceived) {
        byte[] bytes = new byte[2048];
        try (Socket client = server.accept(); InputStream input = client.getInputStream()) {
            client.setSoTimeout(IO_TIMEOUT_MILLIS);
            StringBuilder payload = new StringBuilder();
            while (payload.indexOf("jobs.completed:") < 0) {
                int length = input.read(bytes);
                if (length < 0) {
                    break;
                }
                payload.append(new String(bytes, 0, length, StandardCharsets.UTF_8));
            }
            received.set(payload.toString());
        } catch (Throwable failure) {
            receiverFailure.set(failure);
        } finally {
            expectedMetricReceived.countDown();
        }
    }

    private static final class LocalStatsdConfig implements StatsdConfig {
        private final int port;
        private final StatsdProtocol protocol;

        private LocalStatsdConfig(int port, StatsdProtocol protocol) {
            this.port = port;
            this.protocol = protocol;
        }

        @Override
        public String get(String key) {
            return null;
        }

        @Override
        public String host() {
            return "localhost";
        }

        @Override
        public int port() {
            return port;
        }

        @Override
        public StatsdProtocol protocol() {
            return protocol;
        }

        @Override
        public boolean buffered() {
            return false;
        }

        @Override
        public Duration pollingFrequency() {
            return Duration.ofMillis(100);
        }
    }
}
