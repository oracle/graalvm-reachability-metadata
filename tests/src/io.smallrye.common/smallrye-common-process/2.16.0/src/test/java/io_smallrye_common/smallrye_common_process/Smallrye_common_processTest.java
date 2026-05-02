/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_smallrye_common.smallrye_common_process;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.smallrye.common.process.AbnormalExitException;
import io.smallrye.common.process.PipelineExecutionException;
import io.smallrye.common.process.ProcessBuilder;
import io.smallrye.common.process.ProcessUtil;
import io.smallrye.common.process.WaitableProcessHandle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class Smallrye_common_processTest {
    private static final Duration EXIT_TIMEOUT = Duration.ofSeconds(2);
    private static final Path SHELL = requiredCommand("sh");
    private static final Path CAT = requiredCommand("cat");
    private static final Path GREP = requiredCommand("grep");

    @Test
    void processUtilitiesExposeNativeCharsetJavaCommandAndSearchPath() {
        assertThat(ProcessUtil.nativeCharset()).isNotNull();
        assertThat(ProcessUtil.searchPath()).doesNotContainNull();
        assertThat(ProcessUtil.nameOfJava()).isIn("java", "java.exe");

        Path javaPath = ProcessUtil.pathOfJava();
        assertThat(javaPath).isNotNull();
        if (javaPath.isAbsolute()) {
            assertThat(Files.isExecutable(javaPath)).isTrue();
        } else {
            assertThat(javaPath).isEqualTo(Path.of(ProcessUtil.nameOfJava()));
        }

        assertThat(ProcessUtil.pathOfCommand(SHELL)).contains(SHELL);
        Path missingCommand = Path.of("smallrye-common-process-command-that-should-not-exist");
        assertThat(ProcessUtil.pathOfCommand(missingCommand)).isEmpty();
    }

    @Test
    void execToStringAndStringListCaptureStandardOutput() {
        String singleString = ProcessBuilder.execToString(SHELL, shellArguments("printf 'alpha\\nbeta\\n'"));
        assertThat(singleString).isEqualTo("alpha\nbeta\n");

        List<String> lines = shellCommand("printf 'one\\ntwo\\nthree\\n'")
                .output()
                .toStringList(10, 100)
                .run();

        assertThat(lines).containsExactly("one", "two", "three");
    }

    @Test
    void inputFactoriesCanFeedProcessStandardInput() {
        List<String> fromStrings = ProcessBuilder.newBuilder(CAT)
                .softExitTimeout(EXIT_TIMEOUT)
                .hardExitTimeout(EXIT_TIMEOUT)
                .input()
                .charset(StandardCharsets.UTF_8)
                .fromStrings(List.of("first", "second"))
                .output()
                .charset(StandardCharsets.UTF_8)
                .toStringList(5, 100)
                .run();

        assertThat(fromStrings).containsExactly("first", "second");

        String fromReader = ProcessBuilder.newBuilder(CAT)
                .softExitTimeout(EXIT_TIMEOUT)
                .hardExitTimeout(EXIT_TIMEOUT)
                .input()
                .charset(StandardCharsets.UTF_8)
                .transferFrom(new StringReader("reader-input"))
                .output()
                .charset(StandardCharsets.UTF_8)
                .toSingleString(100)
                .run();

        assertThat(fromReader).isEqualTo("reader-input");

        String fromBytes = ProcessBuilder.newBuilder(CAT)
                .softExitTimeout(EXIT_TIMEOUT)
                .hardExitTimeout(EXIT_TIMEOUT)
                .input()
                .transferFrom(new ByteArrayInputStream("byte-input".getBytes(StandardCharsets.UTF_8)))
                .output()
                .charset(StandardCharsets.UTF_8)
                .toSingleString(100)
                .run();

        assertThat(fromBytes).isEqualTo("byte-input");
    }

    @Test
    void inputAndOutputCanTransferThroughFiles(@TempDir Path tempDir) throws Exception {
        Path input = tempDir.resolve("input.txt");
        Path output = tempDir.resolve("output.txt");
        Files.writeString(input, "from-file\n", StandardCharsets.UTF_8);

        ProcessBuilder.newBuilder(CAT)
                .softExitTimeout(EXIT_TIMEOUT)
                .hardExitTimeout(EXIT_TIMEOUT)
                .input()
                .transferFrom(input)
                .output()
                .transferTo(output)
                .run();

        shellCommand("printf 'appended\\n'")
                .output()
                .appendTo(output)
                .run();

        assertThat(Files.readString(output, StandardCharsets.UTF_8)).isEqualTo("from-file\nappended\n");
    }

    @Test
    void outputCanBeCopiedTransferredAndProcessed() throws Exception {
        ByteArrayOutputStream copiedBytes = new ByteArrayOutputStream();
        String output = shellCommand("printf 'copy-me'")
                .output()
                .charset(StandardCharsets.UTF_8)
                .toSingleString(100)
                .copyAndTransferTo(copiedBytes)
                .run();

        assertThat(output).isEqualTo("copy-me");
        assertThat(copiedBytes.toString(StandardCharsets.UTF_8)).isEqualTo("copy-me");

        StringWriter copiedCharacters = new StringWriter();
        Integer lineCount = shellCommand("printf 'a\\nb\\nc\\n'")
                .output()
                .charset(StandardCharsets.UTF_8)
                .processWith(reader -> (int) reader.lines().count())
                .copyAndTransferTo(copiedCharacters)
                .run();

        assertThat(lineCount).isEqualTo(3);
        assertThat(copiedCharacters.toString()).isEqualTo("a\nb\nc\n");

        Integer byteCount = shellCommand("printf '12345'")
                .output()
                .processBytesWith(inputStream -> inputStream.readAllBytes().length)
                .run();

        assertThat(byteCount).isEqualTo(5);
    }

    @Test
    void environmentAndWorkingDirectoryAreApplied(@TempDir Path tempDir) throws Exception {
        Path marker = tempDir.resolve("marker.txt");
        Files.writeString(marker, "marker", StandardCharsets.UTF_8);

        String output = shellCommand("cat marker.txt; printf ':%s' \"$SMALLRYE_PROCESS_TEST_VALUE\"")
                .directory(tempDir)
                .environment(System.getenv())
                .modifyEnvironment(environment -> environment.put("SMALLRYE_PROCESS_TEST_VALUE", "configured"))
                .output()
                .toSingleString(100)
                .run();

        assertThat(output).isEqualTo("marker:configured");
    }

    @Test
    void errorStreamCanBeRedirectedOrCapturedOnFailure() {
        String redirected = shellCommand("printf 'stdout'; printf ':stderr' >&2")
                .error()
                .redirect()
                .output()
                .toSingleString(100)
                .run();

        assertThat(redirected).contains("stdout").contains(":stderr");

        AbnormalExitException exception = assertThrows(AbnormalExitException.class, () -> shellCommand(
                "printf 'out-head\\nout-tail\\n'; printf 'err-head\\nerr-tail\\n' >&2; exit 7")
                .output()
                .gatherOnFail(true)
                .captureHeadLines(1)
                .captureTailLines(1)
                .error()
                .gatherOnFail(true)
                .captureHeadLines(1)
                .captureTailLines(1)
                .run());

        assertThat(exception.exitCode()).isEqualTo(7);
        assertThat(exception.output()).containsExactly("out-head", "out-tail");
        assertThat(exception.errorOutput()).containsExactly("err-head", "err-tail");
        assertThat(exception.command()).isEqualTo(SHELL);
        assertThat(exception.arguments()).containsExactlyElementsOf(shellArguments(
                "printf 'out-head\\nout-tail\\n'; printf 'err-head\\nerr-tail\\n' >&2; exit 7"));
        assertThat(exception.getMessage()).contains("exit code 7", "out-head", "err-head");
    }

    @Test
    void exitCodeCheckerCanAcceptNonZeroExitStatus() {
        String output = shellCommand("printf 'accepted'; exit 7")
                .exitCodeChecker(exitCode -> exitCode == 7)
                .output()
                .toSingleString(100)
                .run();

        assertThat(output).isEqualTo("accepted");
    }

    @Test
    void programmaticInputProducersCanFeedProcessStandardInput() {
        List<String> producedCharacters = ProcessBuilder.newBuilder(CAT)
                .softExitTimeout(EXIT_TIMEOUT)
                .hardExitTimeout(EXIT_TIMEOUT)
                .input()
                .charset(StandardCharsets.UTF_8)
                .produceWith(writer -> writer.write("writer-line-one\nwriter-line-two\n"))
                .output()
                .charset(StandardCharsets.UTF_8)
                .toStringList(5, 100)
                .run();

        assertThat(producedCharacters).containsExactly("writer-line-one", "writer-line-two");

        String producedBytes = ProcessBuilder.newBuilder(CAT)
                .softExitTimeout(EXIT_TIMEOUT)
                .hardExitTimeout(EXIT_TIMEOUT)
                .input()
                .produceBytesWith(outputStream -> outputStream.write(
                        "bytes-produced".getBytes(StandardCharsets.UTF_8)))
                .output()
                .charset(StandardCharsets.UTF_8)
                .toSingleString(100)
                .run();

        assertThat(producedBytes).isEqualTo("bytes-produced");
    }

    @Test
    void errorLinesCanBeConsumedWithoutChangingOutputResult() {
        List<String> errorLines = new ArrayList<>();

        String output = shellCommand("printf 'stdout-result'; printf 'warning-one\\nwarning-two\\n' >&2")
                .output()
                .toSingleString(100)
                .error()
                .consumeLinesWith(100, errorLines::add)
                .run();

        assertThat(output).isEqualTo("stdout-result");
        assertThat(errorLines).containsExactly("warning-one", "warning-two");
    }

    @Test
    void pipelineFeedsOutputIntoNextProcess() {
        List<String> output = shellCommand("printf 'alpha\\nbravo\\ncharlie\\n'")
                .output()
                .pipeTo(GREP, "bravo")
                .output()
                .toStringList(5, 100)
                .run();

        assertThat(output).containsExactly("bravo");
    }

    @Test
    void pipelineFailureReportsProcessExceptions() {
        PipelineExecutionException exception = assertThrows(PipelineExecutionException.class,
                () -> shellCommand("exit 3")
                        .output()
                        .pipeTo(SHELL, "-c", "cat >/dev/null; exit 4")
                        .run());

        assertThat(exception.processExecutionExceptions())
                .hasSize(2)
                .allSatisfy(processException -> assertThat(processException.command()).isEqualTo(SHELL));
    }

    @Test
    void asynchronousExecutionInvokesWhileRunningHandler() throws Exception {
        AtomicReference<WaitableProcessHandle> seenHandle = new AtomicReference<>();
        CompletableFuture<String> future = shellCommand("printf 'async-output'")
                .whileRunning(seenHandle::set)
                .output()
                .toSingleString(100)
                .runAsync();

        assertThat(future.get(10, TimeUnit.SECONDS)).isEqualTo("async-output");
        assertThat(seenHandle.get()).isNotNull();
        assertThat(seenHandle.get().pid()).isPositive();
        assertThat(seenHandle.get().command()).isEqualTo(SHELL);
        assertThat(seenHandle.get().arguments()).containsExactlyElementsOf(shellArguments("printf 'async-output'"));
        assertThat(seenHandle.get().waitUninterruptiblyFor(1, TimeUnit.SECONDS)).isTrue();
        assertThat(seenHandle.get().exitValue()).isZero();
    }

    @Test
    void processUtilityCanObserveAndDestroyPlainJdkProcesses() throws Exception {
        java.lang.Process finished = new java.lang.ProcessBuilder(SHELL.toString(), "-c", "exit 0").start();
        assertThat(ProcessUtil.stillRunningAfter(finished, Duration.ofSeconds(5))).isFalse();

        java.lang.Process running = new java.lang.ProcessBuilder(SHELL.toString(), "-c", "read ignored").start();
        try {
            assertThat(ProcessUtil.stillRunningAfter(running, Duration.ofMillis(10))).isTrue();
            ProcessUtil.destroyAllForcibly(running);
            assertThat(ProcessUtil.stillRunningAfter(running, Duration.ofSeconds(5))).isFalse();
        } finally {
            ProcessUtil.destroyAllForcibly(running);
        }
    }

    private static ProcessBuilder<Void> shellCommand(String script) {
        return ProcessBuilder.newBuilder(SHELL, shellArguments(script))
                .softExitTimeout(EXIT_TIMEOUT)
                .hardExitTimeout(EXIT_TIMEOUT);
    }

    private static List<String> shellArguments(String script) {
        return List.of("-c", script);
    }

    private static Path requiredCommand(String command) {
        Optional<Path> path = ProcessUtil.pathOfCommand(Path.of(command));
        return path.orElseThrow(
                () -> new IllegalStateException("Required command is not available on PATH: " + command));
    }
}
