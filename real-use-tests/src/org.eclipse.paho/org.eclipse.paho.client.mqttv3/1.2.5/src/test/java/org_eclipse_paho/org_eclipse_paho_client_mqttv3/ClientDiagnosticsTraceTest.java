/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_paho.org_eclipse_paho_client_mqttv3;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.eclipse.paho.client.mqttv3.util.Debug;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.net.ConnectException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.MemoryHandler;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Covers the client's diagnostics: `java.util.logging` trace emitted through the
/// default `JSR47Logger` and its `logcat` message catalog, the `Debug` state dump that
/// pushes a buffered `MemoryHandler`, and localized `MqttException` messages.
///
/// The scenario is a field gateway whose operator turns on Paho tracing in memory,
/// runs a publish round trip, asks the client for a debug dump when support requests
/// it, and reads the human-readable reason of each failure it later reports.
public class ClientDiagnosticsTraceTest {

    private static final String IMAGE = "eclipse-mosquitto:2.0.22";

    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(90);

    private static final long TIMEOUT_MS = 10_000;

    private static final String PAHO_LOGGER = "org.eclipse.paho.client.mqttv3";

    private static final String CLIENT_ID = "diag-gateway";

    /// Held strongly: `LogManager` keeps loggers weakly, so a collected logger would
    /// lose the level and handler configured here.
    private static Logger pahoLogger;

    private static Level previousLevel;

    private static boolean previousUseParentHandlers;

    private static MemoryHandler traceBuffer;

    private static CapturingHandler supportLog;

    private static String containerName;

    private static String serverUri;

    @BeforeAll
    static void startBroker() throws Exception {
        containerName = "paho-diagnostics-" + System.nanoTime();
        docker("run", "-d", "--name", containerName, "-p", "127.0.0.1::1883", IMAGE,
                "mosquitto", "-c", "/mosquitto-no-auth.conf");
        String mapping = docker("port", containerName, "1883/tcp").lines().findFirst().orElseThrow();
        serverUri = "tcp://127.0.0.1:" + mapping.substring(mapping.lastIndexOf(':') + 1);
        awaitBroker();

        pahoLogger = Logger.getLogger(PAHO_LOGGER);
        previousLevel = pahoLogger.getLevel();
        previousUseParentHandlers = pahoLogger.getUseParentHandlers();
        supportLog = new CapturingHandler();
        traceBuffer = new MemoryHandler(supportLog, 50_000, Level.OFF);
        traceBuffer.setLevel(Level.ALL);
        pahoLogger.addHandler(traceBuffer);
        pahoLogger.setUseParentHandlers(false);
        pahoLogger.setLevel(Level.FINE);
    }

    @AfterAll
    static void stopBroker() throws Exception {
        if (pahoLogger != null) {
            pahoLogger.removeHandler(traceBuffer);
            pahoLogger.setLevel(previousLevel);
            pahoLogger.setUseParentHandlers(previousUseParentHandlers);
        }
        if (containerName != null) {
            docker("rm", "-f", "-v", containerName);
        }
    }

    @Test
    void debugDumpPushesBufferedTraceWithClientState() throws Exception {
        MqttClient gateway = new MqttClient(serverUri, CLIENT_ID, new MemoryPersistence());
        try {
            gateway.connect(options());
            BlockingQueue<String> readings = new LinkedBlockingQueue<>();
            gateway.subscribe("site/7/readings", 1, (topic, message) ->
                    readings.add(new String(message.getPayload(), StandardCharsets.UTF_8)));
            MqttMessage reading = new MqttMessage("flow=12.5".getBytes(StandardCharsets.UTF_8));
            reading.setQos(1);
            gateway.publish("site/7/readings", reading);
            assertThat(readings.poll(TIMEOUT_MS, TimeUnit.MILLISECONDS)).isEqualTo("flow=12.5");
            assertThat(supportLog.messages()).isEmpty();

            Debug debug = gateway.getDebug();
            debug.dumpClientDebug();

            List<LogRecord> pushed = supportLog.records();
            assertThat(pushed).isNotEmpty().allSatisfy(record -> assertThat(record.getLevel()).isEqualTo(Level.FINE));
            List<String> gatewayTrace = supportLog.messagesFrom(CLIENT_ID);
            assertThat(gatewayTrace).contains(
                    CLIENT_ID + ": cleanSession=true connectionTimeout=10 TimekeepAlive=30 userName=null "
                            + "password=[null] will=[null] userContext=null callback=null",
                    CLIENT_ID + ": state=CONNECTING",
                    CLIENT_ID + ": state=CONNECTED");
            assertThat(pushed).anySatisfy(record -> {
                assertThat(record.getLoggerName()).isEqualTo("org.eclipse.paho.client.mqttv3.internal.ClientComms");
                assertThat(record.getMessage()).isEqualTo(CLIENT_ID + ": state=CONNECTED");
            });

            String comms = supportLog.messageFromMethod("dumpClientComms");
            assertThat(comms).contains(CLIENT_ID + " : ClientComms").contains("serverURI").contains(serverUri);
            String connectOptions = supportLog.messageFromMethod("dumpConOptions");
            assertThat(connectOptions).contains(CLIENT_ID + " : Connect Options")
                    .containsPattern("KeepAliveInterval\\s+:\\s+30")
                    .containsPattern("CleanSession\\s+:\\s+true")
                    .containsPattern("MqttVersion\\s+:\\s+4")
                    .containsPattern("WillDestination\\s+:\\s+null");
            String clientState = supportLog.messageFromMethod("dumpClientState");
            assertThat(clientState).contains(CLIENT_ID + " : ClientState")
                    .containsPattern("maxInflight\\s+:\\s+10")
                    .containsPattern("actualInFlight\\s+:\\s+0")
                    .containsPattern("quiescing\\s+:\\s+false");
            assertThat(supportLog.messageFromMethod("dumpVersion")).contains("Version Info").contains("Build Level");
            assertThat(supportLog.messageFromMethod("dumpSystemProperties")).contains("SystemProperties")
                    .contains("java.version");

            supportLog.clear();
            gateway.disconnect();
            assertThat(gateway.isConnected()).isFalse();
            traceBuffer.push();
            assertThat(supportLog.messagesFrom(CLIENT_ID)).contains(CLIENT_ID + ": state=DISCONNECTING");
        } finally {
            if (gateway.isConnected()) {
                gateway.disconnectForcibly(1_000, 1_000);
            }
            gateway.close();
        }
    }

    @Test
    void failuresCarryLocalizedReasons() throws Exception {
        MqttClient idle = new MqttClient(serverUri, CLIENT_ID + "-idle", new MemoryPersistence());
        try {
            assertThatThrownBy(() -> idle.publish("site/7/readings", new MqttMessage(new byte[] {1})))
                    .isInstanceOfSatisfying(MqttException.class, e -> {
                        assertThat(e.getReasonCode()).isEqualTo(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
                        assertThat(e.getMessage()).isEqualTo("Client is not connected");
                        assertThat(e).hasToString("Client is not connected (32104)");
                    });
        } finally {
            idle.close();
        }

        MqttClient offline = new MqttClient("tcp://127.0.0.1:1", CLIENT_ID + "-offline", new MemoryPersistence());
        try {
            MqttConnectOptions options = options();
            options.setConnectionTimeout(5);
            assertThatThrownBy(() -> offline.connect(options))
                    .isInstanceOfSatisfying(MqttException.class, e -> {
                        assertThat(e.getReasonCode()).isEqualTo(MqttException.REASON_CODE_SERVER_CONNECT_ERROR);
                        assertThat(e.getMessage()).isEqualTo("Unable to connect to server");
                        assertThat(e.getCause()).isInstanceOf(ConnectException.class);
                        assertThat(e.toString()).startsWith("Unable to connect to server (32103) - ");
                    });
        } finally {
            offline.close();
        }

        MqttClient closed = new MqttClient(serverUri, CLIENT_ID + "-closed", new MemoryPersistence());
        closed.close();
        assertThatThrownBy(() -> closed.connect(options()))
                .isInstanceOfSatisfying(MqttException.class, e -> {
                    assertThat(e.getReasonCode()).isEqualTo(MqttException.REASON_CODE_CLIENT_CLOSED);
                    assertThat(e.getMessage()).isEqualTo("Client is closed");
                });
    }

    /// The support log the in-memory trace buffer pushes to when a dump is requested.
    private static final class CapturingHandler extends Handler {

        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public synchronized void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        synchronized List<LogRecord> records() {
            return new ArrayList<>(records);
        }

        synchronized void clear() {
            records.clear();
        }

        List<String> messages() {
            return records().stream().map(LogRecord::getMessage).collect(Collectors.toList());
        }

        List<String> messagesFrom(String clientId) {
            return messages().stream().filter(message -> message.startsWith(clientId + ": "))
                    .collect(Collectors.toList());
        }

        String messageFromMethod(String sourceMethod) {
            return records().stream()
                    .filter(record -> sourceMethod.equals(record.getSourceMethodName()))
                    .filter(record -> record.getMessage().startsWith(CLIENT_ID + ": "))
                    .map(LogRecord::getMessage)
                    .reduce((first, second) -> second)
                    .orElseThrow(() -> new AssertionError("No trace record from " + sourceMethod));
        }
    }

    private static MqttConnectOptions options() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MqttConnectOptions.MQTT_VERSION_3_1_1);
        options.setCleanSession(true);
        options.setAutomaticReconnect(false);
        options.setKeepAliveInterval(30);
        options.setConnectionTimeout(10);
        return options;
    }

    private static void awaitBroker() throws Exception {
        long deadline = System.nanoTime() + STARTUP_TIMEOUT.toNanos();
        MqttException last = null;
        while (System.nanoTime() < deadline) {
            MqttClient probe = new MqttClient(serverUri, "startup-probe-" + System.nanoTime(), new MemoryPersistence());
            try {
                probe.connect(options());
                probe.disconnect();
                return;
            } catch (MqttException e) {
                last = e;
            } finally {
                if (probe.isConnected()) {
                    probe.disconnectForcibly(1_000, 1_000);
                }
                probe.close();
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("Mosquitto did not accept connections in time", last);
    }

    private static String docker(String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("docker");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output;
        try (InputStream stream = process.getInputStream()) {
            output = new String(stream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue()).as(output).isZero();
        return output;
    }
}
