/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_dropwizard_metrics.metrics_graphite;

import com.codahale.metrics.Clock;
import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricAttribute;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Reservoir;
import com.codahale.metrics.Snapshot;
import com.codahale.metrics.Timer;
import com.codahale.metrics.graphite.Graphite;
import com.codahale.metrics.graphite.GraphiteReporter;
import com.codahale.metrics.graphite.GraphiteSender;
import com.codahale.metrics.graphite.GraphiteUDP;
import com.codahale.metrics.graphite.PickledGraphite;
import org.junit.jupiter.api.Test;

import javax.net.SocketFactory;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Metrics_graphiteTest {

    private static final InetAddress LOOPBACK = InetAddress.getLoopbackAddress();

    @Test
    void reporterSendsRegistryMetricsWithConfiguredNamesFormatsUnitsAndLifecycle() {
        MetricRegistry registry = new MetricRegistry();
        registry.register("temperature", (Gauge<Double>) () -> 12.3456D);
        registry.register("healthy", (Gauge<Boolean>) () -> true);
        registry.register("text.gauge", (Gauge<String>) () -> "ignored");
        registry.register("excluded.counter", new Counter());

        Counter requests = registry.counter("requests");
        requests.inc(7L);

        Histogram payload = new Histogram(new FixedReservoir(new FixedSnapshot(1_000_000L, 5_000_000L, 3_000_000.25D,
                125_000.5D)));
        payload.update(1L);
        payload.update(2L);
        registry.register("payload", payload);

        MutableClock meterClock = new MutableClock(0L, 0L);
        Meter throughput = new Meter(meterClock);
        meterClock.add(2L, TimeUnit.SECONDS);
        throughput.mark(4L);
        registry.register("throughput", throughput);

        MutableClock timerClock = new MutableClock(0L, 0L);
        Timer latency = new Timer(new FixedReservoir(new FixedSnapshot(1_000_000L, 5_000_000L, 3_000_000.25D,
                125_000.5D)), timerClock);
        timerClock.add(4L, TimeUnit.SECONDS);
        latency.update(2L, TimeUnit.MILLISECONDS);
        registry.register("latency", latency);

        CapturingGraphiteSender sender = new CapturingGraphiteSender();
        GraphiteReporter reporter = GraphiteReporter.forRegistry(registry)
                .withClock(new MutableClock(123_456_789_000L, 0L))
                .prefixedWith("app")
                .convertRatesTo(TimeUnit.MINUTES)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .filter((name, metric) -> !name.startsWith("excluded"))
                .disabledMetricAttributes(EnumSet.of(
                        MetricAttribute.M1_RATE,
                        MetricAttribute.M5_RATE,
                        MetricAttribute.M15_RATE,
                        MetricAttribute.STDDEV,
                        MetricAttribute.P999))
                .withFloatingPointFormatter(value -> "fp:" + String.format(Locale.US, "%.3f", value))
                .build(sender);

        reporter.report();

        assertThat(sender.connectCalls).isEqualTo(1);
        assertThat(sender.flushCalls).isEqualTo(1);
        assertThat(sender.closeCalls).isEqualTo(1);
        assertThat(sender.isConnected()).isFalse();

        Map<String, Point> pointsByName = sender.pointsByName();
        assertPoint(pointsByName, "app.temperature", "fp:12.346", 123_456_789L);
        assertPoint(pointsByName, "app.healthy", "1", 123_456_789L);
        assertPoint(pointsByName, "app.requests.count", "7", 123_456_789L);
        assertThat(pointsByName).doesNotContainKey("app.text.gauge");
        assertThat(pointsByName).doesNotContainKey("app.excluded.counter.count");

        assertPoint(pointsByName, "app.payload.count", "2", 123_456_789L);
        assertPoint(pointsByName, "app.payload.max", "5000000", 123_456_789L);
        assertPoint(pointsByName, "app.payload.mean", "fp:3000000.250", 123_456_789L);
        assertPoint(pointsByName, "app.payload.min", "1000000", 123_456_789L);
        assertPoint(pointsByName, "app.payload.p50", "fp:2500000.000", 123_456_789L);
        assertThat(pointsByName).doesNotContainKey("app.payload.stddev");
        assertThat(pointsByName).doesNotContainKey("app.payload.p999");

        assertPoint(pointsByName, "app.throughput.count", "4", 123_456_789L);
        assertPoint(pointsByName, "app.throughput.mean_rate", "fp:120.000", 123_456_789L);
        assertThat(pointsByName).doesNotContainKey("app.throughput.m1_rate");

        assertPoint(pointsByName, "app.latency.count", "1", 123_456_789L);
        assertPoint(pointsByName, "app.latency.max", "fp:5.000", 123_456_789L);
        assertPoint(pointsByName, "app.latency.mean", "fp:3.000", 123_456_789L);
        assertPoint(pointsByName, "app.latency.min", "fp:1.000", 123_456_789L);
        assertPoint(pointsByName, "app.latency.p75", "fp:3.750", 123_456_789L);
        assertPoint(pointsByName, "app.latency.mean_rate", "fp:15.000", 123_456_789L);
    }

    @Test
    void reporterCanEmitMetricAttributesAsGraphiteTags() {
        MetricRegistry registry = new MetricRegistry();
        registry.counter("jobs").inc(3L);
        CapturingGraphiteSender sender = new CapturingGraphiteSender();
        GraphiteReporter reporter = GraphiteReporter.forRegistry(registry)
                .withClock(new MutableClock(5_000L, 0L))
                .addMetricAttributesAsTags(true)
                .build(sender);

        reporter.report();

        assertPoint(sender.pointsByName(), "jobs;metricattribute=count", "3", 5L);
        assertThat(sender.pointsByName()).doesNotContainKey("jobs.count");
    }

    @Test
    void tcpGraphiteSenderWritesSanitizedLineProtocol() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, LOOPBACK)) {
            CompletableFuture<String> receivedLine = CompletableFuture.supplyAsync(
                    () -> readSingleTcpLine(serverSocket));
            Graphite graphite = new Graphite(new InetSocketAddress(LOOPBACK, serverSocket.getLocalPort()));

            assertThat(graphite.isConnected()).isFalse();
            graphite.connect();
            assertThat(graphite.isConnected()).isTrue();
            assertThatThrownBy(graphite::connect).isInstanceOf(IllegalStateException.class);

            graphite.send("  tcp metric\nname ", " 12 34 ", 42L);
            graphite.flush();
            graphite.close();

            assertThat(receivedLine.get(5L, TimeUnit.SECONDS)).isEqualTo("tcp-metric-name 12-34 42");
            assertThat(graphite.getFailures()).isZero();
            assertThat(graphite.isConnected()).isFalse();
        }
    }

    @Test
    void tcpGraphiteSenderUsesConfiguredCharsetWhenWritingLineProtocol() throws Exception {
        Charset charset = StandardCharsets.ISO_8859_1;
        try (ServerSocket serverSocket = new ServerSocket(0, 1, LOOPBACK)) {
            CompletableFuture<byte[]> receivedLine = CompletableFuture.supplyAsync(
                    () -> readSingleTcpLineBytes(serverSocket));
            Graphite graphite = new Graphite(new InetSocketAddress(LOOPBACK, serverSocket.getLocalPort()),
                    SocketFactory.getDefault(), charset);

            graphite.connect();
            graphite.send("accented.metric", "caf\u00e9", 314L);
            graphite.flush();
            graphite.close();

            assertThat(receivedLine.get(5L, TimeUnit.SECONDS))
                    .containsExactly("accented.metric caf\u00e9 314\n".getBytes(charset));
            assertThat(graphite.getFailures()).isZero();
            assertThat(graphite.isConnected()).isFalse();
        }
    }

    @Test
    void udpGraphiteSenderWritesSanitizedDatagramProtocol() throws Exception {
        try (DatagramSocket socket = new DatagramSocket(new InetSocketAddress(LOOPBACK, 0))) {
            socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(5L));
            GraphiteUDP graphite = new GraphiteUDP(new InetSocketAddress(LOOPBACK, socket.getLocalPort()));

            assertThat(graphite.isConnected()).isFalse();
            graphite.connect();
            assertThat(graphite.isConnected()).isTrue();
            assertThatThrownBy(graphite::connect).isInstanceOf(IllegalStateException.class);

            graphite.send(" udp metric ", "5 6", 77L);
            graphite.flush();

            byte[] buffer = new byte[256];
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet);
            String datagram = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                    StandardCharsets.UTF_8);

            assertThat(datagram).isEqualTo("udp-metric 5-6 77\n");
            assertThat(graphite.getFailures()).isZero();
            graphite.close();
            assertThat(graphite.isConnected()).isFalse();
        }
    }

    @Test
    void pickledGraphiteSenderFramesBatchedPicklePayloads() throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(0, 1, LOOPBACK)) {
            CompletableFuture<byte[]> receivedPayload = CompletableFuture.supplyAsync(
                    () -> readSinglePickleFrame(serverSocket));
            PickledGraphite graphite = new PickledGraphite(
                    new InetSocketAddress(LOOPBACK, serverSocket.getLocalPort()), 2);

            graphite.connect();
            assertThat(graphite.isConnected()).isTrue();
            graphite.send(" first metric ", "1 2", 100L);
            graphite.send("second\nmetric", "3", 200L);
            graphite.close();

            String payload = new String(receivedPayload.get(5L, TimeUnit.SECONDS), StandardCharsets.UTF_8);
            assertThat(payload).startsWith("(l").endsWith(".");
            assertThat(payload).contains("S'first-metric'", "L100L", "S'1-2'");
            assertThat(payload).contains("S'second-metric'", "L200L", "S'3'");
            assertThat(graphite.getFailures()).isZero();
            assertThat(graphite.isConnected()).isFalse();
        }
    }

    @Test
    void pickledGraphiteSenderFlushesPartialBatchWithConfiguredCharset() throws Exception {
        Charset charset = StandardCharsets.ISO_8859_1;
        try (ServerSocket serverSocket = new ServerSocket(0, 1, LOOPBACK)) {
            CompletableFuture<byte[]> receivedPayload = CompletableFuture.supplyAsync(
                    () -> readSinglePickleFrame(serverSocket));
            PickledGraphite graphite = new PickledGraphite(
                    new InetSocketAddress(LOOPBACK, serverSocket.getLocalPort()), SocketFactory.getDefault(), charset,
                    10);

            graphite.connect();
            graphite.send("caf\u00e9.metric", "ol\u00e9", 123L);
            graphite.flush();

            assertThat(new String(receivedPayload.get(5L, TimeUnit.SECONDS), charset))
                    .isEqualTo("(l(S'caf\u00e9.metric'\n(L123L\nS'ol\u00e9'\ntta.");
            assertThat(graphite.getFailures()).isZero();
            graphite.close();
            assertThat(graphite.isConnected()).isFalse();
        }
    }

    @Test
    void graphiteConstructorRejectsInvalidTcpEndpointConfiguration() {
        assertThatThrownBy(() -> new Graphite("", 2003)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hostname");
        assertThatThrownBy(() -> new Graphite("localhost", -1)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
        assertThatThrownBy(() -> new Graphite("localhost", 65_536)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("port");
    }

    private static void assertPoint(Map<String, Point> pointsByName, String name, String value, long timestamp) {
        assertThat(pointsByName).containsEntry(name, new Point(name, value, timestamp));
    }

    private static String readSingleTcpLine(ServerSocket serverSocket) {
        try (Socket socket = serverSocket.accept();
             BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(),
                     StandardCharsets.UTF_8))) {
            socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(5L));
            return reader.readLine();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read Graphite TCP line", e);
        }
    }

    private static byte[] readSingleTcpLineBytes(ServerSocket serverSocket) {
        try (Socket socket = serverSocket.accept();
             InputStream input = socket.getInputStream()) {
            socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(5L));
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int nextByte;
            while ((nextByte = input.read()) != -1) {
                output.write(nextByte);
                if (nextByte == '\n') {
                    return output.toByteArray();
                }
            }
            return output.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read Graphite TCP line bytes", e);
        }
    }

    private static byte[] readSinglePickleFrame(ServerSocket serverSocket) {
        try (Socket socket = serverSocket.accept();
             DataInputStream input = new DataInputStream(socket.getInputStream())) {
            socket.setSoTimeout((int) TimeUnit.SECONDS.toMillis(5L));
            int payloadLength = input.readInt();
            byte[] payload = new byte[payloadLength];
            input.readFully(payload);
            return payload;
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read Graphite pickle frame", e);
        }
    }

    private static final class CapturingGraphiteSender implements GraphiteSender {
        private final List<Point> points = new ArrayList<>();
        private boolean connected;
        private int connectCalls;
        private int flushCalls;
        private int closeCalls;

        @Override
        public void connect() {
            connected = true;
            connectCalls++;
        }

        @Override
        public void send(String name, String value, long timestamp) {
            points.add(new Point(name, value, timestamp));
        }

        @Override
        public void flush() {
            flushCalls++;
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public int getFailures() {
            return 0;
        }

        @Override
        public void close() {
            connected = false;
            closeCalls++;
        }

        private Map<String, Point> pointsByName() {
            Map<String, Point> pointsByName = new LinkedHashMap<>();
            for (Point point : points) {
                pointsByName.put(point.name, point);
            }
            return pointsByName;
        }
    }

    private static final class Point {
        private final String name;
        private final String value;
        private final long timestamp;

        private Point(String name, String value, long timestamp) {
            this.name = name;
            this.value = value;
            this.timestamp = timestamp;
        }

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Point)) {
                return false;
            }
            Point point = (Point) other;
            return timestamp == point.timestamp
                    && name.equals(point.name)
                    && value.equals(point.value);
        }

        @Override
        public int hashCode() {
            int result = name.hashCode();
            result = 31 * result + value.hashCode();
            result = 31 * result + Long.hashCode(timestamp);
            return result;
        }

        @Override
        public String toString() {
            return name + '=' + value + '@' + timestamp;
        }
    }

    private static final class MutableClock extends Clock {
        private long timeMillis;
        private long tickNanos;

        private MutableClock(long timeMillis, long tickNanos) {
            this.timeMillis = timeMillis;
            this.tickNanos = tickNanos;
        }

        @Override
        public long getTick() {
            return tickNanos;
        }

        @Override
        public long getTime() {
            return timeMillis;
        }

        private void add(long amount, TimeUnit unit) {
            timeMillis += unit.toMillis(amount);
            tickNanos += unit.toNanos(amount);
        }
    }

    private static final class FixedReservoir implements Reservoir {
        private final Snapshot snapshot;
        private int size;

        private FixedReservoir(Snapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public int size() {
            return size;
        }

        @Override
        public void update(long value) {
            size++;
        }

        @Override
        public Snapshot getSnapshot() {
            return snapshot;
        }
    }

    private static final class FixedSnapshot extends Snapshot {
        private final long min;
        private final long max;
        private final double mean;
        private final double stdDev;

        private FixedSnapshot(long min, long max, double mean, double stdDev) {
            this.min = min;
            this.max = max;
            this.mean = mean;
            this.stdDev = stdDev;
        }

        @Override
        public double getValue(double quantile) {
            return max * quantile;
        }

        @Override
        public long[] getValues() {
            return new long[] {min, max};
        }

        @Override
        public int size() {
            return 2;
        }

        @Override
        public long getMax() {
            return max;
        }

        @Override
        public double getMean() {
            return mean;
        }

        @Override
        public long getMin() {
            return min;
        }

        @Override
        public double getStdDev() {
            return stdDev;
        }

        @Override
        public void dump(java.io.OutputStream output) {
            try {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                for (long value : getValues()) {
                    buffer.write(Long.toString(value).getBytes(StandardCharsets.UTF_8));
                    buffer.write('\n');
                }
                output.write(buffer.toByteArray());
            } catch (IOException e) {
                throw new IllegalStateException("Unable to dump fixed snapshot", e);
            }
        }
    }
}
