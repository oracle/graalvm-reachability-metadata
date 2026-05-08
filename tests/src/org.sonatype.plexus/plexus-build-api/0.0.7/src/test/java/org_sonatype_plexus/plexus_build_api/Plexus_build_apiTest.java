/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_plexus.plexus_build_api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.codehaus.plexus.util.Scanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sonatype.plexus.build.incremental.BuildContext;
import org.sonatype.plexus.build.incremental.DefaultBuildContext;
import org.sonatype.plexus.build.incremental.EmptyScanner;
import org.sonatype.plexus.build.incremental.ThreadBuildContext;

public class Plexus_build_apiTest {
    @TempDir
    Path temporaryDirectory;

    @AfterEach
    void resetThreadBuildContext() {
        ThreadBuildContext.setThreadBuildContext(new DefaultBuildContext());
    }

    @Test
    void defaultContextTreatsEveryInputAsChangedAndStoresNoValues() throws IOException {
        DefaultBuildContext context = new DefaultBuildContext();
        File file = Files.createFile(temporaryDirectory.resolve("input.txt")).toFile();
        List<File> files = List.of(file);

        assertThat(context.hasDelta("src/main/resources/application.properties")).isTrue();
        assertThat(context.hasDelta(file)).isTrue();
        assertThat(context.hasDelta(files)).isTrue();
        assertThat(context.isIncremental()).isFalse();

        context.setValue("answer", 42);

        assertThat(context.getValue("answer")).isNull();
        assertThatCode(() -> context.refresh(file)).doesNotThrowAnyException();
        assertThatCode(() -> context.removeMessages(file)).doesNotThrowAnyException();
    }

    @Test
    void defaultContextCreatesOutputStreamsForRegularFiles() throws IOException {
        DefaultBuildContext context = new DefaultBuildContext();
        Path output = temporaryDirectory.resolve("generated.txt");

        try (OutputStream stream = context.newFileOutputStream(output.toFile())) {
            stream.write("generated content".getBytes(StandardCharsets.UTF_8));
        }

        assertThat(Files.readString(output, StandardCharsets.UTF_8)).isEqualTo("generated content");
    }

    @Test
    void defaultScannerScansBaseDirectoryWithConfiguredPatterns() throws IOException {
        DefaultBuildContext context = new DefaultBuildContext();
        Files.writeString(temporaryDirectory.resolve("root.txt"), "root", StandardCharsets.UTF_8);
        Files.createDirectories(temporaryDirectory.resolve("nested"));
        Files.writeString(temporaryDirectory.resolve("nested/child.txt"), "child", StandardCharsets.UTF_8);
        Files.writeString(temporaryDirectory.resolve("nested/excluded.txt"), "excluded", StandardCharsets.UTF_8);
        Files.writeString(temporaryDirectory.resolve("nested/child.bin"), "binary", StandardCharsets.UTF_8);

        Scanner scanner = context.newScanner(temporaryDirectory.toFile(), true);
        scanner.setIncludes(new String[] {"*.txt", "**/*.txt"});
        scanner.setExcludes(new String[] {"nested/excluded.txt"});
        scanner.scan();

        assertThat(scanner.getBasedir()).isEqualTo(temporaryDirectory.toFile());
        assertThat(normalized(scanner.getIncludedFiles()))
                .containsExactlyInAnyOrder("root.txt", "nested/child.txt");
    }

    @Test
    void deleteScannerAndEmptyScannerNeverReportIncludedPaths() {
        DefaultBuildContext context = new DefaultBuildContext();
        File baseDirectory = temporaryDirectory.toFile();

        Scanner deleteScanner = context.newDeleteScanner(baseDirectory);
        deleteScanner.setIncludes(new String[] {"*", "**/*"});
        deleteScanner.setExcludes(new String[] {"ignored"});
        deleteScanner.addDefaultExcludes();
        deleteScanner.scan();

        assertThat(deleteScanner).isInstanceOf(EmptyScanner.class);
        assertThat(deleteScanner.getBasedir()).isEqualTo(baseDirectory);
        assertThat(deleteScanner.getIncludedFiles()).isEmpty();
        assertThat(deleteScanner.getIncludedDirectories()).isEmpty();

        EmptyScanner emptyScanner = new EmptyScanner(baseDirectory);
        emptyScanner.scan();

        assertThat(emptyScanner.getBasedir()).isEqualTo(baseDirectory);
        assertThat(emptyScanner.getIncludedFiles()).isEmpty();
        assertThat(emptyScanner.getIncludedDirectories()).isEmpty();
    }

    @Test
    void uptodateCheckRequiresExistingNewerTargetAndExistingSource() throws IOException {
        DefaultBuildContext context = new DefaultBuildContext();
        File older = Files.createFile(temporaryDirectory.resolve("older.txt")).toFile();
        File newer = Files.createFile(temporaryDirectory.resolve("newer.txt")).toFile();
        assertThat(older.setLastModified(10_000L)).isTrue();
        assertThat(newer.setLastModified(20_000L)).isTrue();

        assertThat(context.isUptodate(newer, older)).isTrue();
        assertThat(context.isUptodate(older, newer)).isFalse();
        assertThat(context.isUptodate(null, older)).isFalse();
        assertThat(context.isUptodate(newer, null)).isFalse();
        assertThat(context.isUptodate(new File(temporaryDirectory.toFile(), "missing.txt"), older)).isFalse();
    }

    @Test
    void defaultContextLogsSupportedMessageSeveritiesAndRejectsUnknownSeverity() throws IOException {
        DefaultBuildContext context = new DefaultBuildContext();
        context.enableLogging(new ConsoleLogger(Logger.LEVEL_DISABLED, "test"));
        File file = Files.createFile(temporaryDirectory.resolve("messages.txt")).toFile();
        RuntimeException cause = new RuntimeException("diagnostic cause");

        assertThatCode(() -> context.addWarning(file, 7, 3, "warning message", cause)).doesNotThrowAnyException();
        assertThatCode(() -> context.addError(file, 8, 4, "error message", cause)).doesNotThrowAnyException();
        assertThatThrownBy(() -> context.addMessage(file, 1, 1, "unknown", 99, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("severity=99");
    }

    @Test
    void plexusContainerDiscoversDefaultBuildContextComponent() throws Exception {
        PlexusContainer container = new DefaultPlexusContainer();
        try {
            container.initialize();
            container.start();
            String role = BuildContext.class.getName();

            assertThat(container.hasComponent(role, "default")).isTrue();
            assertThat(container.lookup(role, "default")).isInstanceOf(DefaultBuildContext.class);
        } finally {
            container.dispose();
        }
    }

    @Test
    void threadBuildContextKeepsContextsIsolatedPerThreadAndFallsBackToDefault() throws Exception {
        ThreadBuildContext.setThreadBuildContext(null);
        BuildContext defaultContext = ThreadBuildContext.getContext();

        assertThat(defaultContext).isInstanceOf(DefaultBuildContext.class);
        assertThat(defaultContext.hasDelta("unconfigured-thread.txt")).isTrue();

        RecordingBuildContext mainThreadContext = new RecordingBuildContext();
        ThreadBuildContext.setThreadBuildContext(mainThreadContext);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<?> result = executor.submit(() -> {
                try {
                    BuildContext workerDefaultContext = ThreadBuildContext.getContext();
                    assertThat(workerDefaultContext).isInstanceOf(DefaultBuildContext.class);
                    assertThat(workerDefaultContext).isNotSameAs(mainThreadContext);

                    RecordingBuildContext workerThreadContext = new RecordingBuildContext();
                    ThreadBuildContext.setThreadBuildContext(workerThreadContext);

                    assertThat(ThreadBuildContext.getContext()).isSameAs(workerThreadContext);
                } finally {
                    ThreadBuildContext.setThreadBuildContext(null);
                }
            });
            result.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }

        assertThat(ThreadBuildContext.getContext()).isSameAs(mainThreadContext);
    }

    @Test
    void threadBuildContextDelegatesAllOperationsToConfiguredContext() throws IOException {
        RecordingBuildContext recordingContext = new RecordingBuildContext();
        ThreadBuildContext.setThreadBuildContext(recordingContext);
        ThreadBuildContext context = new ThreadBuildContext();
        File file = Files.createFile(temporaryDirectory.resolve("delegated.txt")).toFile();
        List<File> files = List.of(file);

        assertThat(context.hasDelta("path")).isFalse();
        assertThat(recordingContext.lastOperation).isEqualTo("hasDelta(String):path");
        assertThat(context.hasDelta(file)).isTrue();
        assertThat(recordingContext.lastOperation).isEqualTo("hasDelta(File):delegated.txt");
        assertThat(context.hasDelta(files)).isFalse();
        assertThat(recordingContext.lastOperation).isEqualTo("hasDelta(List):1");
        assertThat(context.isIncremental()).isTrue();

        context.setValue("key", "value");
        assertThat(recordingContext.getValue("key")).isEqualTo("value");
        try (OutputStream stream = context.newFileOutputStream(file)) {
            stream.write('x');
        }
        assertThat(recordingContext.output.toString(StandardCharsets.UTF_8)).isEqualTo("x");

        assertThat(context.newScanner(file)).isSameAs(recordingContext.scanner);
        assertThat(context.newScanner(file, false)).isSameAs(recordingContext.scanner);
        assertThat(context.newDeleteScanner(file)).isSameAs(recordingContext.deleteScanner);
        assertThat(context.isUptodate(file, file)).isTrue();

        context.refresh(file);
        assertThat(recordingContext.lastOperation).isEqualTo("refresh:delegated.txt");
        context.addWarning(file, 1, 2, "warning", null);
        assertThat(recordingContext.lastOperation).isEqualTo("message:1:warning");
        context.addError(file, 3, 4, "error", null);
        assertThat(recordingContext.lastOperation).isEqualTo("message:2:error");
        context.removeMessages(file);
        assertThat(recordingContext.lastOperation).isEqualTo("removeMessages:delegated.txt");
    }

    private static List<String> normalized(String[] paths) {
        return Arrays.stream(paths)
                .map(path -> path.replace(File.separatorChar, '/'))
                .toList();
    }

    private static final class RecordingBuildContext implements BuildContext {
        private final Scanner scanner = new EmptyScanner(new File("scan-base"));
        private final Scanner deleteScanner = new EmptyScanner(new File("delete-base"));
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private String key;
        private Object value;
        private String lastOperation;

        @Override
        public boolean hasDelta(String relpath) {
            lastOperation = "hasDelta(String):" + relpath;
            return false;
        }

        @Override
        public boolean hasDelta(File file) {
            lastOperation = "hasDelta(File):" + file.getName();
            return true;
        }

        @Override
        public boolean hasDelta(List relpaths) {
            lastOperation = "hasDelta(List):" + relpaths.size();
            return false;
        }

        @Override
        public void refresh(File file) {
            lastOperation = "refresh:" + file.getName();
        }

        @Override
        public OutputStream newFileOutputStream(File file) {
            lastOperation = "newFileOutputStream:" + file.getName();
            return output;
        }

        @Override
        public Scanner newScanner(File basedir) {
            lastOperation = "newScanner:" + basedir.getName();
            return scanner;
        }

        @Override
        public Scanner newDeleteScanner(File basedir) {
            lastOperation = "newDeleteScanner:" + basedir.getName();
            return deleteScanner;
        }

        @Override
        public Scanner newScanner(File basedir, boolean ignoreDelta) {
            lastOperation = "newScanner:" + basedir.getName() + ':' + ignoreDelta;
            return scanner;
        }

        @Override
        public boolean isIncremental() {
            lastOperation = "isIncremental";
            return true;
        }

        @Override
        public void setValue(String key, Object value) {
            this.key = key;
            this.value = value;
            lastOperation = "setValue:" + key;
        }

        @Override
        public Object getValue(String key) {
            lastOperation = "getValue:" + key;
            return key.equals(this.key) ? value : null;
        }

        @Override
        public void addWarning(File file, int line, int column, String message, Throwable cause) {
            addMessage(file, line, column, message, SEVERITY_WARNING, cause);
        }

        @Override
        public void addError(File file, int line, int column, String message, Throwable cause) {
            addMessage(file, line, column, message, SEVERITY_ERROR, cause);
        }

        @Override
        public void addMessage(File file, int line, int column, String message, int severity, Throwable cause) {
            lastOperation = "message:" + severity + ':' + message;
        }

        @Override
        public void removeMessages(File file) {
            lastOperation = "removeMessages:" + file.getName();
        }

        @Override
        public boolean isUptodate(File target, File source) {
            lastOperation = "isUptodate:" + target.getName() + ':' + source.getName();
            return true;
        }
    }
}
