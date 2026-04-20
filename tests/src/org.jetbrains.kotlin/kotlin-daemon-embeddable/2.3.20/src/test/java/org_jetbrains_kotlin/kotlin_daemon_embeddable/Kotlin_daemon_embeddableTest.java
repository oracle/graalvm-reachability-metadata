/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jetbrains_kotlin.kotlin_daemon_embeddable;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import kotlin.Unit;
import kotlin.jvm.functions.Function2;
import kotlin.sequences.SequencesKt;
import org.jetbrains.kotlin.cli.common.CompilerSystemProperties;
import org.jetbrains.kotlin.daemon.RemoteInputStreamClient;
import org.jetbrains.kotlin.daemon.RemoteOutputStreamClient;
import org.jetbrains.kotlin.daemon.RunningCompilations;
import org.jetbrains.kotlin.daemon.common.ClientUtilsKt;
import org.jetbrains.kotlin.daemon.common.CompilerId;
import org.jetbrains.kotlin.daemon.common.DaemonJVMOptions;
import org.jetbrains.kotlin.daemon.common.DaemonOptions;
import org.jetbrains.kotlin.daemon.common.DaemonParamsKt;
import org.jetbrains.kotlin.daemon.common.DaemonReportCategory;
import org.jetbrains.kotlin.daemon.common.DaemonWithMetadata;
import org.jetbrains.kotlin.daemon.common.DummyProfiler;
import org.jetbrains.kotlin.daemon.common.LoopbackNetworkInterface;
import org.jetbrains.kotlin.daemon.common.RemoteInputStream;
import org.jetbrains.kotlin.daemon.common.RemoteOutputStream;
import org.jetbrains.kotlin.progress.CompilationCanceledException;
import org.jetbrains.kotlin.progress.CompilationCanceledStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Kotlin_daemon_embeddableTest {

    @Test
    void configureDaemonOptionsUsesSystemPropertiesAndDefaults(@TempDir Path tempDir) {
        Map<CompilerSystemProperties, String> previousValues = snapshotProperties(
                CompilerSystemProperties.COMPILE_DAEMON_OPTIONS_PROPERTY,
                CompilerSystemProperties.COMPILE_DAEMON_VERBOSE_REPORT_PROPERTY,
                CompilerSystemProperties.COMPILE_DAEMON_CUSTOM_RUN_FILES_PATH_FOR_TESTS
        );
        try {
            Path configuredRunFilesPath = tempDir.resolve("configured-run-files");
            Path defaultRunFilesPath = tempDir.resolve("default-run-files");
            CompilerSystemProperties.COMPILE_DAEMON_OPTIONS_PROPERTY.setValue(
                    "runFilesPath=" + configuredRunFilesPath
                            + ",autoshutdownIdleSeconds=42"
                            + ",autoshutdownUnusedSeconds=17"
                            + ",shutdownDelayMilliseconds=2500"
                            + ",reportPerf"
            );
            CompilerSystemProperties.COMPILE_DAEMON_VERBOSE_REPORT_PROPERTY.setValue("true");
            CompilerSystemProperties.COMPILE_DAEMON_CUSTOM_RUN_FILES_PATH_FOR_TESTS.setValue(defaultRunFilesPath.toString());

            DaemonOptions options = DaemonParamsKt.configureDaemonOptions(new DaemonOptions());

            assertThat(options.getRunFilesPath()).isEqualTo(configuredRunFilesPath.toString());
            assertThat(options.getAutoshutdownIdleSeconds()).isEqualTo(42);
            assertThat(options.getAutoshutdownUnusedSeconds()).isEqualTo(17);
            assertThat(options.getShutdownDelayMilliseconds()).isEqualTo(2500L);
            assertThat(options.getVerbose()).isTrue();
            assertThat(options.getReportPerf()).isTrue();
            assertThat(DaemonParamsKt.getRunFilesPathOrDefault(options)).isEqualTo(configuredRunFilesPath.toString());

            options.setRunFilesPath("");
            assertThat(DaemonParamsKt.getRunFilesPathOrDefault(options)).isEqualTo(defaultRunFilesPath.toString());
        } finally {
            restoreProperties(previousValues);
        }
    }

    @Test
    void configureDaemonJVMOptionsParsesMemoryFlagsAndRetainsRemainingJvmParams() {
        Map<CompilerSystemProperties, String> previousValues = snapshotProperties(
                CompilerSystemProperties.COMPILE_DAEMON_JVM_OPTIONS_PROPERTY
        );
        try {
            CompilerSystemProperties.COMPILE_DAEMON_JVM_OPTIONS_PROPERTY.setValue(
                    "\"-Xmx768m,-XX:MaxMetaspaceSize=256m,-Dsample.flag=a\\,b,-Dfeature.enabled=true\""
            );

            DaemonJVMOptions options = DaemonParamsKt.configureDaemonJVMOptions(
                    new DaemonJVMOptions(),
                    List.of("-Dextra=value"),
                    false,
                    false,
                    false
            );

            assertThat(options.getMaxMemory()).isEqualTo("768m");
            assertThat(options.getMaxMetaspaceSize()).isEqualTo("256m");
            assertThat(options.getReservedCodeCacheSize()).isEqualTo("320m");
            assertThat(options.getJvmParams()).containsExactly(
                    "Dsample.flag=a,b",
                    "Dfeature.enabled=true",
                    "-Dextra=value",
                    "ea"
            );
        } finally {
            restoreProperties(previousValues);
        }
    }

    @Test
    void memoryComparisonAndUpperBoundUpdatesRespectHumanReadableSizes() {
        DaemonJVMOptions smaller = new DaemonJVMOptions();
        smaller.setMaxMemory("512m");
        smaller.setMaxMetaspaceSize("128m");
        smaller.setReservedCodeCacheSize("128m");

        DaemonJVMOptions larger = new DaemonJVMOptions();
        larger.setMaxMemory("1g");
        larger.setMaxMetaspaceSize("256m");
        larger.setReservedCodeCacheSize("192m");

        assertThat(DaemonParamsKt.memorywiseFitsInto(smaller, larger)).isTrue();
        assertThat(DaemonParamsKt.memorywiseFitsInto(larger, smaller)).isFalse();
        assertThat(DaemonParamsKt.compareDaemonJVMOptionsMemory(smaller, larger)).isEqualTo(-1);
        assertThat(DaemonParamsKt.compareDaemonJVMOptionsMemory(larger, smaller)).isEqualTo(1);

        DaemonJVMOptions updated = DaemonParamsKt.updateMemoryUpperBounds(smaller, larger);
        assertThat(updated).isSameAs(smaller);
        assertThat(updated.getMaxMemory()).isEqualTo("1g");
        assertThat(updated.getMaxMetaspaceSize()).isEqualTo("256m");
        assertThat(updated.getReservedCodeCacheSize()).isEqualTo("192m");
    }

    @Test
    void loopbackSocketFactoriesExchangeDataOverLoopback() throws Exception {
        LoopbackNetworkInterface loopback = LoopbackNetworkInterface.INSTANCE;
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (ServerSocket serverSocket = loopback.getServerLoopbackSocketFactory().createServerSocket(0)) {
            Future<String> acceptedRemoteAddress = executor.submit(() -> {
                try (Socket acceptedSocket = serverSocket.accept()) {
                    int received = acceptedSocket.getInputStream().read();
                    acceptedSocket.getOutputStream().write(received + 1);
                    acceptedSocket.getOutputStream().flush();
                    return acceptedSocket.getInetAddress().getHostAddress();
                }
            });

            try (Socket clientSocket = loopback.getClientLoopbackSocketFactory()
                    .createSocket(loopback.getLoopbackInetAddressName(), serverSocket.getLocalPort())) {
                clientSocket.getOutputStream().write(41);
                clientSocket.getOutputStream().flush();

                assertThat(clientSocket.getInputStream().read()).isEqualTo(42);
                assertThat(clientSocket.getInetAddress().isLoopbackAddress()).isTrue();
            }

            String remoteAddress = acceptedRemoteAddress.get(5, TimeUnit.SECONDS);
            assertThat(InetAddress.getByName(remoteAddress).isLoopbackAddress()).isTrue();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void compilerIdDigestIsStableAcrossClasspathOrderAndDuplicates() {
        CompilerId orderedWithDuplicates = new CompilerId(List.of("b.jar", "a.jar", "a.jar"), "embedded-test");
        CompilerId orderedWithoutDuplicates = new CompilerId(List.of("a.jar", "b.jar"), "other-version");
        CompilerId differentClasspath = new CompilerId(List.of("a.jar", "c.jar"), "embedded-test");

        assertThat(orderedWithDuplicates.digest()).isEqualTo(orderedWithoutDuplicates.digest());
        assertThat(orderedWithDuplicates.digest()).isNotEqualTo(differentClasspath.digest());
        assertThat(orderedWithDuplicates.digest()).hasSize(32).matches("[0-9a-f]+$");
    }

    @Test
    void walkDaemonsDeletesOrphanedRunFiles(@TempDir Path tempDir) throws Exception {
        CompilerId compilerId = new CompilerId(List.of(tempDir.resolve("compiler.jar").toString()), "embedded-test");
        Path runFile = tempDir.resolve(ClientUtilsKt.makeRunFilenameString(
                "2024-04-20T12:34:56.789Z",
                compilerId.digest(),
                "17042",
                ""
        ));
        Files.createFile(runFile);
        Files.setLastModifiedTime(runFile, FileTime.fromMillis(System.currentTimeMillis() - 1_500_000L));

        Path referenceFile = tempDir.resolve("reference.txt");
        Files.writeString(referenceFile, "reference");

        List<String> reports = new java.util.ArrayList<>();
        List<DaemonWithMetadata> daemons = SequencesKt.toList(ClientUtilsKt.walkDaemons(
                tempDir.toFile(),
                compilerId,
                referenceFile.toFile(),
                (Function2<File, Integer, Boolean>) (file, daemonPort) -> true,
                (Function2<DaemonReportCategory, String, Unit>) (category, message) -> {
                    reports.add(category + ":" + message);
                    return Unit.INSTANCE;
                }
        ));

        assertThat(daemons).isEmpty();
        assertThat(Files.exists(runFile)).isFalse();
        assertThat(reports).anySatisfy(report -> assertThat(report).contains("seemingly orphaned run file"));
    }

    @Test
    void runningCompilationStatusStaysActiveUntilCompilationIsRemoved() {
        RunningCompilations runningCompilations = new RunningCompilations();
        int compilationId = 101;
        runningCompilations.add(compilationId);

        CompilationCanceledStatus status = runningCompilations.getCompilationCanceledStatus(compilationId);

        assertThatCode(status::checkCanceled).doesNotThrowAnyException();

        runningCompilations.remove(compilationId);

        assertThatThrownBy(status::checkCanceled).isInstanceOf(CompilationCanceledException.class);
    }

    @Test
    void cancelAllCancelsEveryTrackedCompilationStatus() {
        RunningCompilations runningCompilations = new RunningCompilations();
        runningCompilations.add(7);
        runningCompilations.add(9);

        CompilationCanceledStatus firstStatus = runningCompilations.getCompilationCanceledStatus(7);
        CompilationCanceledStatus secondStatus = runningCompilations.getCompilationCanceledStatus(9);

        assertThatCode(firstStatus::checkCanceled).doesNotThrowAnyException();
        assertThatCode(secondStatus::checkCanceled).doesNotThrowAnyException();

        runningCompilations.cancelAll();

        assertThatThrownBy(firstStatus::checkCanceled).isInstanceOf(CompilationCanceledException.class);
        assertThatThrownBy(secondStatus::checkCanceled).isInstanceOf(CompilationCanceledException.class);
    }

    @Test
    void remoteOutputStreamClientForwardsWholePartialAndSingleByteWrites() throws Exception {
        RecordingRemoteOutputStream remoteOutputStream = new RecordingRemoteOutputStream();
        RemoteOutputStreamClient client = new RemoteOutputStreamClient(remoteOutputStream, new DummyProfiler());

        client.write(new byte[]{10, 20});
        client.write(new byte[]{30, 40, 50}, 1, 2);
        client.write(60);

        assertThat(remoteOutputStream.toByteArray()).containsExactly(10, 20, 40, 50, 60);
    }

    @Test
    void remoteInputStreamClientReadsRequestedRangesWithoutTouchingOtherBytes() throws Exception {
        RemoteInputStreamClient client = new RemoteInputStreamClient(
                new ChunkedRemoteInputStream(new byte[]{10, 20, 30, 40}),
                new DummyProfiler()
        );

        assertThat(client.read()).isEqualTo(10);

        byte[] destination = new byte[]{99, 99, 99, 99, 99};
        int bytesRead = client.read(destination, 1, 2);

        assertThat(bytesRead).isEqualTo(2);
        assertThat(destination).containsExactly(99, 20, 30, 99, 99);
        assertThat(client.read()).isEqualTo(40);
    }

    private static Map<CompilerSystemProperties, String> snapshotProperties(CompilerSystemProperties... properties) {
        Map<CompilerSystemProperties, String> previousValues = new EnumMap<>(CompilerSystemProperties.class);
        for (CompilerSystemProperties property : properties) {
            previousValues.put(property, property.getValue());
        }
        return previousValues;
    }

    private static void restoreProperties(Map<CompilerSystemProperties, String> previousValues) {
        for (Map.Entry<CompilerSystemProperties, String> entry : previousValues.entrySet()) {
            if (entry.getValue() == null) {
                entry.getKey().clear();
            } else {
                entry.getKey().setValue(entry.getValue());
            }
        }
    }

    private static final class RecordingRemoteOutputStream implements RemoteOutputStream {

        private final ByteArrayOutputStream writtenBytes = new ByteArrayOutputStream();

        @Override
        public void close() {
        }

        @Override
        public void write(byte[] data, int offset, int length) {
            writtenBytes.write(data, offset, length);
        }

        @Override
        public void write(int value) {
            writtenBytes.write(value);
        }

        byte[] toByteArray() {
            return writtenBytes.toByteArray();
        }
    }

    private static final class ChunkedRemoteInputStream implements RemoteInputStream {

        private final byte[] source;
        private int nextIndex;

        private ChunkedRemoteInputStream(byte[] source) {
            this.source = Arrays.copyOf(source, source.length);
        }

        @Override
        public void close() {
        }

        @Override
        public byte[] read(int length) {
            int remaining = source.length - nextIndex;
            int bytesToRead = Math.min(length, remaining);
            byte[] chunk = Arrays.copyOfRange(source, nextIndex, nextIndex + bytesToRead);
            nextIndex += bytesToRead;
            return chunk;
        }

        @Override
        public int read() {
            if (nextIndex >= source.length) {
                return -1;
            }
            return source[nextIndex++] & 0xFF;
        }
    }

}
