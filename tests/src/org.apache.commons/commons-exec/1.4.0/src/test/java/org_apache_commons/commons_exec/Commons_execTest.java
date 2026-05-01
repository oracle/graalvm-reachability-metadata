/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_exec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecuteResultHandler;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;
import org.apache.commons.exec.ExecuteWatchdog;
import org.apache.commons.exec.LogOutputStream;
import org.apache.commons.exec.OS;
import org.apache.commons.exec.ProcessDestroyer;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.commons.exec.ShutdownHookProcessDestroyer;
import org.apache.commons.exec.TimeoutObserver;
import org.apache.commons.exec.Watchdog;
import org.apache.commons.exec.environment.EnvironmentUtils;
import org.apache.commons.exec.util.MapUtils;
import org.apache.commons.exec.util.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Commons_execTest {
    private static final String ENVIRONMENT_VARIABLE_NAME = "COMMONS_EXEC_VALUE";

    @TempDir
    Path temporaryDirectory;

    @Test
    void commandLineParsesQuotesSubstitutesVariablesAndPreservesExplicitArgumentBoundaries() {
        Map<String, Object> substitutions = new HashMap<>();
        substitutions.put("name", "value with spaces");
        substitutions.put("flag", "enabled");

        CommandLine commandLine = CommandLine.parse("tool --name '${name}' plain", substitutions)
                .addArgument("${flag}")
                .addArgument("literal space", false)
                .addArguments(new String[] {"--quoted", "two words"});

        assertThat(commandLine.getExecutable()).isEqualTo("tool");
        assertThat(commandLine.isFile()).isFalse();
        assertThat(commandLine.getArguments()).containsExactly(
                "--name",
                "\"value with spaces\"",
                "plain",
                "enabled",
                "literal space",
                "--quoted",
                "\"two words\"");
        assertThat(commandLine.toStrings()).containsExactly(
                "tool",
                "--name",
                "\"value with spaces\"",
                "plain",
                "enabled",
                "literal space",
                "--quoted",
                "\"two words\"");
        assertThat(commandLine.toString()).contains("tool", "--quoted", "two words");

        CommandLine copiedCommandLine = new CommandLine(commandLine);
        commandLine.setSubstitutionMap(Map.of("name", "changed", "flag", "disabled"));
        assertThat(copiedCommandLine.getArguments()).contains("\"value with spaces\"", "enabled");
    }

    @Test
    void commandLineCreatedFromFileMarksExecutableAsFile() {
        File executableFile = temporaryDirectory.resolve("executable with spaces").toFile();
        CommandLine commandLine = new CommandLine(executableFile).addArgument("argument with spaces");

        assertThat(commandLine.isFile()).isTrue();
        assertThat(commandLine.getExecutable()).isEqualTo(executableFile.getAbsolutePath());
        assertThat(commandLine.getArguments()).containsExactly("\"argument with spaces\"");
    }

    @Test
    void defaultExecutorRunsProcessWithEnvironmentWorkingDirectoryAndPumpedStreams() throws Exception {
        Path markerFile = temporaryDirectory.resolve("marker.txt");
        Files.writeString(markerFile, "marker-from-working-directory", StandardCharsets.UTF_8);

        Map<String, String> environment = EnvironmentUtils.getProcEnvironment();
        environment.put(ENVIRONMENT_VARIABLE_NAME, "env-value");

        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        ByteArrayInputStream stdin = new ByteArrayInputStream("payload-from-stdin\n".getBytes(StandardCharsets.UTF_8));

        PumpStreamHandler streamHandler = new PumpStreamHandler(stdout, stderr, stdin);
        DefaultExecutor executor = DefaultExecutor.builder()
                .setWorkingDirectory(temporaryDirectory.toFile())
                .setExecuteStreamHandler(streamHandler)
                .get();

        int exitValue = executor.execute(shellCommand(
                "printf 'env:%s\\n' \"$" + ENVIRONMENT_VARIABLE_NAME
                        + "\"; printf 'marker:'; cat marker.txt; printf '\\n'; IFS= read line; "
                        + "printf 'stdin:%s\\n' \"$line\"; printf 'err:%s\\n' \"$" + ENVIRONMENT_VARIABLE_NAME
                        + "\" >&2",
                "echo env:!" + ENVIRONMENT_VARIABLE_NAME
                        + "! & set /p marker=<marker.txt & echo marker:!marker! & set /p line= & "
                        + "echo stdin:!line! & echo err:!" + ENVIRONMENT_VARIABLE_NAME + "! 1>&2"),
                environment);

        assertThat(exitValue).isZero();
        assertThat(lines(stdout)).contains(
                "env:env-value",
                "marker:marker-from-working-directory",
                "stdin:payload-from-stdin");
        assertThat(lines(stderr)).contains("err:env-value");
        assertThat(executor.getWorkingDirectory()).isEqualTo(temporaryDirectory.toFile());
        assertThat(executor.getStreamHandler()).isSameAs(streamHandler);
    }

    @Test
    void defaultExecutorRegistersLaunchedProcessesWithProcessDestroyer() throws Exception {
        RecordingProcessDestroyer destroyer = new RecordingProcessDestroyer();
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        DefaultExecutor executor = DefaultExecutor.builder()
                .setExecuteStreamHandler(new PumpStreamHandler(stdout, new ByteArrayOutputStream()))
                .get();
        executor.setProcessDestroyer(destroyer);

        int exitValue = executor.execute(shellCommand("printf 'destroyer-ok\\n'", "echo destroyer-ok"));

        assertThat(exitValue).isZero();
        assertThat(lines(stdout)).containsExactly("destroyer-ok");
        assertThat(executor.getProcessDestroyer()).isSameAs(destroyer);
        assertThat(destroyer.addedProcesses()).hasSize(1);
        assertThat(destroyer.removedProcesses()).containsExactlyElementsOf(destroyer.addedProcesses());
        assertThat(destroyer.size()).isZero();
    }

    @Test
    void executorReportsUnexpectedExitValuesAndAllowsAdditionalSuccessCodes() throws Exception {
        DefaultExecutor executor = DefaultExecutor.builder().get();

        assertThat(executor.isFailure(0)).isFalse();
        assertThat(executor.isFailure(7)).isTrue();

        ExecuteException exception = catchThrowableOfType(
                () -> executor.execute(shellCommand("exit 7", "exit /b 7")),
                ExecuteException.class);

        assertThat(exception).isNotNull();
        assertThat(exception.getExitValue()).isEqualTo(7);
        assertThat(exception).hasMessageContaining("7");

        executor.setExitValues(new int[] {0, 7});
        assertThat(executor.isFailure(7)).isFalse();
        assertThat(executor.execute(shellCommand("exit 7", "exit /b 7"))).isEqualTo(7);

        executor.setExitValue(3);
        assertThat(executor.isFailure(0)).isTrue();
        assertThat(executor.isFailure(3)).isFalse();
    }

    @Test
    void asynchronousExecutionCompletesThroughDefaultResultHandler() throws Exception {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        DefaultExecutor executor = DefaultExecutor.builder()
                .setExecuteStreamHandler(new PumpStreamHandler(stdout, stderr))
                .get();

        DefaultExecuteResultHandler resultHandler = new DefaultExecuteResultHandler();
        executor.execute(shellCommand("printf 'async-ok\\n'", "echo async-ok"), resultHandler);
        resultHandler.waitFor(Duration.ofSeconds(5L));

        assertThat(resultHandler.hasResult()).isTrue();
        assertThat(resultHandler.getExitValue()).isZero();
        assertThat(resultHandler.getException()).isNull();
        assertThat(lines(stdout)).contains("async-ok");
        assertThat(lines(stderr)).isEmpty();
    }

    @Test
    void watchdogTerminatesLongRunningProcesses() throws Exception {
        DefaultExecutor executor = DefaultExecutor.builder()
                .setExecuteStreamHandler(new PumpStreamHandler(new ByteArrayOutputStream(), new ByteArrayOutputStream()))
                .get();
        ExecuteWatchdog watchdog = ExecuteWatchdog.builder()
                .setTimeout(Duration.ofMillis(500L))
                .get();
        executor.setWatchdog(watchdog);

        ExecuteException exception = catchThrowableOfType(
                () -> executor.execute(shellCommand("sleep 5", "ping -n 6 127.0.0.1 > nul")),
                ExecuteException.class);

        assertThat(exception).isNotNull();
        assertThat(watchdog.killedProcess()).isTrue();
        assertThat(watchdog.isWatching()).isFalse();
        assertThat(executor.getWatchdog()).isSameAs(watchdog);
    }

    @Test
    void watchdogNotifiesRegisteredObserversAndHonorsRemovalAndStop() throws Exception {
        CountDownLatch timeoutNotification = new CountDownLatch(1);
        RecordingTimeoutObserver notifiedObserver = new RecordingTimeoutObserver(timeoutNotification);
        RecordingTimeoutObserver removedObserver = new RecordingTimeoutObserver(new CountDownLatch(1));
        Watchdog watchdog = Watchdog.builder()
                .setThreadFactory(Executors.defaultThreadFactory())
                .setTimeout(Duration.ofMillis(100L))
                .get();

        watchdog.addTimeoutObserver(notifiedObserver);
        watchdog.addTimeoutObserver(removedObserver);
        watchdog.removeTimeoutObserver(removedObserver);
        watchdog.start();

        assertThat(timeoutNotification.await(5L, TimeUnit.SECONDS)).isTrue();
        assertThat(notifiedObserver.notifications()).isEqualTo(1);
        assertThat(notifiedObserver.lastWatchdog()).isSameAs(watchdog);
        assertThat(removedObserver.notifications()).isZero();

        CountDownLatch stoppedNotification = new CountDownLatch(1);
        RecordingTimeoutObserver stoppedObserver = new RecordingTimeoutObserver(stoppedNotification);
        Watchdog stoppedWatchdog = Watchdog.builder()
                .setThreadFactory(Executors.defaultThreadFactory())
                .setTimeout(Duration.ofSeconds(1L))
                .get();
        stoppedWatchdog.addTimeoutObserver(stoppedObserver);
        stoppedWatchdog.start();
        stoppedWatchdog.stop();

        assertThat(stoppedNotification.await(200L, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(stoppedObserver.notifications()).isZero();
    }

    @Test
    void logOutputStreamCollectsCompleteLinesOnFlushAndClose() throws Exception {
        CapturingLogOutputStream stream = new CapturingLogOutputStream(42);

        stream.write("first line\nsecond".getBytes(StandardCharsets.UTF_8));
        assertThat(stream.lines()).containsExactly("first line");

        stream.flush();
        assertThat(stream.lines()).containsExactly("first line", "second");

        stream.write("third\r\nfourth".getBytes(StandardCharsets.UTF_8));
        stream.close();

        assertThat(stream.lines()).containsExactly("first line", "second", "third", "fourth");
        assertThat(stream.getMessageLevel()).isEqualTo(42);
    }

    @Test
    void environmentAndUtilityHelpersCopyPrefixMergeQuoteAndSubstitute() {
        Map<String, String> environment = new LinkedHashMap<>();
        EnvironmentUtils.addVariableToEnvironment(environment, "ALPHA=one=two");
        environment.put("BETA", "three");

        assertThat(environment).containsEntry("ALPHA", "one=two");
        assertThat(EnvironmentUtils.toStrings(environment)).contains("ALPHA=one=two", "BETA=three");

        Map<String, String> base = new LinkedHashMap<>();
        base.put("key", "base");
        base.put("only-base", "present");
        Map<String, String> overrides = Map.of("key", "override", "only-override", "present");

        Map<String, String> copied = MapUtils.copy(base);
        copied.put("copy-only", "present");
        assertThat(base).doesNotContainKey("copy-only");
        assertThat(MapUtils.prefix(base, "prefix")).containsEntry("prefix.key", "base");
        assertThat(MapUtils.merge(base, overrides)).containsEntry("key", "override")
                .containsEntry("only-base", "present")
                .containsEntry("only-override", "present");
        assertThat(MapUtils.copy(null)).isNull();

        assertThat(StringUtils.stringSubstitution("Hello ${name}", Map.of("name", "Commons Exec"), false).toString())
                .isEqualTo("Hello Commons Exec");
        assertThat(StringUtils.stringSubstitution("Keep ${missing}", Map.of(), true).toString())
                .isEqualTo("Keep ${missing}");
        assertThat(StringUtils.quoteArgument("two words")).isEqualTo("\"two words\"");
        assertThat(StringUtils.quoteArgument("has\"double")).isEqualTo("'has\"double'");
        assertThat(StringUtils.isQuoted("'single quoted'")).isTrue();
        assertThatThrownBy(() -> StringUtils.quoteArgument("both'and\"inside"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("single and double quotes");
    }

    @Test
    void shutdownHookProcessDestroyerTracksAndDestroysRegisteredProcesses() {
        ShutdownHookProcessDestroyer destroyer = new ShutdownHookProcessDestroyer();
        TrackingProcess process = new TrackingProcess();

        assertThat(destroyer.size()).isZero();
        assertThat(destroyer.add(process)).isTrue();
        assertThat(destroyer.size()).isEqualTo(1);
        assertThat(destroyer.isAddedAsShutdownHook()).isTrue();

        assertThat(destroyer.remove(process)).isTrue();
        assertThat(destroyer.size()).isZero();

        assertThat(destroyer.add(process)).isTrue();
        destroyer.run();
        assertThat(process.destroyed()).isTrue();
        assertThat(destroyer.remove(process)).isTrue();
        assertThat(destroyer.size()).isZero();
    }

    private static CommandLine shellCommand(String unixScript, String windowsScript) {
        if (OS.isFamilyWindows()) {
            return new CommandLine("cmd")
                    .addArgument("/v:on")
                    .addArgument("/d")
                    .addArgument("/c")
                    .addArgument(windowsScript, false);
        }
        return new CommandLine("/bin/sh")
                .addArgument("-c")
                .addArgument(unixScript, false);
    }

    private static List<String> lines(ByteArrayOutputStream stream) {
        String normalizedOutput = stream.toString(StandardCharsets.UTF_8)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .trim();
        if (normalizedOutput.isEmpty()) {
            return List.of();
        }
        return Arrays.asList(normalizedOutput.split("\\n"));
    }

    private static final class RecordingTimeoutObserver implements TimeoutObserver {
        private final AtomicInteger notifications = new AtomicInteger();
        private final CountDownLatch notificationLatch;
        private volatile Watchdog lastWatchdog;

        private RecordingTimeoutObserver(CountDownLatch notificationLatch) {
            this.notificationLatch = notificationLatch;
        }

        private int notifications() {
            return notifications.get();
        }

        private Watchdog lastWatchdog() {
            return lastWatchdog;
        }

        @Override
        public void timeoutOccured(Watchdog watchdog) {
            lastWatchdog = watchdog;
            notifications.incrementAndGet();
            notificationLatch.countDown();
        }
    }

    private static final class CapturingLogOutputStream extends LogOutputStream {
        private final List<String> lines = new ArrayList<>();

        private CapturingLogOutputStream(int level) {
            super(level);
        }

        private List<String> lines() {
            return lines;
        }

        @Override
        protected void processLine(String line, int level) {
            lines.add(line);
        }
    }

    private static final class RecordingProcessDestroyer implements ProcessDestroyer {
        private final List<Process> activeProcesses = new ArrayList<>();
        private final List<Process> addedProcesses = new ArrayList<>();
        private final List<Process> removedProcesses = new ArrayList<>();

        private List<Process> addedProcesses() {
            return addedProcesses;
        }

        private List<Process> removedProcesses() {
            return removedProcesses;
        }

        @Override
        public boolean add(Process process) {
            activeProcesses.add(process);
            addedProcesses.add(process);
            return true;
        }

        @Override
        public boolean remove(Process process) {
            removedProcesses.add(process);
            return activeProcesses.remove(process);
        }

        @Override
        public int size() {
            return activeProcesses.size();
        }
    }

    private static final class TrackingProcess extends Process {
        private boolean destroyed;

        private boolean destroyed() {
            return destroyed;
        }

        @Override
        public OutputStream getOutputStream() {
            return new ByteArrayOutputStream();
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            destroyed = true;
        }
    }
}
