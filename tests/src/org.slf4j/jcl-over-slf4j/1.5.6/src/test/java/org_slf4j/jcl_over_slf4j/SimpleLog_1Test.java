/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.jcl_over_slf4j;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class SimpleLog_1Test {

    private static final String SIMPLELOG_PROPERTIES = """
            org.apache.commons.logging.simplelog.showlogname=true
            org.apache.commons.logging.simplelog.showShortLogname=false
            """;

    private static final String BOOTSTRAP_RUNNER_SOURCE = """
            import org.apache.commons.logging.impl.SimpleLog;

            public final class BootstrapSimpleLogMain {
                public static void main(String[] args) {
                    Thread.currentThread().setContextClassLoader(null);

                    System.out.println("bootstrap=" + (SimpleLog.class.getClassLoader() == null));

                    SimpleLog simpleLog = new SimpleLog("example.error.logger");
                    simpleLog.setLevel(SimpleLog.LOG_LEVEL_ALL);
                    simpleLog.error("error message", new IllegalStateException("boom"));
                }
            }
            """;

    @TempDir
    Path tempDir;

    @Test
    void simpleLogLoadsConfigurationFromSystemResourcesWhenBootstrapLoaded() throws Exception {
        Path libraryJar = Path.of(readResource("tested-library-path.txt"));
        Path sourceDir = Files.createDirectories(tempDir.resolve("src"));
        Path classesDir = Files.createDirectories(tempDir.resolve("classes"));
        Path resourcesDir = Files.createDirectories(tempDir.resolve("resources"));
        Path runnerSource = sourceDir.resolve("BootstrapSimpleLogMain.java");

        Files.writeString(runnerSource, BOOTSTRAP_RUNNER_SOURCE, StandardCharsets.UTF_8);
        Files.writeString(resourcesDir.resolve("simplelog.properties"), SIMPLELOG_PROPERTIES, StandardCharsets.UTF_8);

        Process compileProcess = new ProcessBuilder(
                javaExecutable("javac").toString(),
                "-cp",
                libraryJar.toString(),
                "-d",
                classesDir.toString(),
                runnerSource.toString()
        ).redirectErrorStream(true).start();

        String compileOutput = readProcessOutput(compileProcess);
        int compileExitCode = compileProcess.waitFor();

        assertThat(compileExitCode)
                .withFailMessage("javac failed:%n%s", compileOutput)
                .isZero();

        String bootClasspath = Stream.of(libraryJar, resourcesDir)
                .map(Path::toString)
                .collect(Collectors.joining(File.pathSeparator));

        Process runProcess = new ProcessBuilder(
                javaExecutable("java").toString(),
                "-cp",
                classesDir.toString(),
                "-Xbootclasspath/a:" + bootClasspath,
                "BootstrapSimpleLogMain"
        ).redirectErrorStream(true).start();

        String output = readProcessOutput(runProcess);
        int exitCode = runProcess.waitFor();

        assertThat(exitCode)
                .withFailMessage("bootstrap run failed:%n%s", output)
                .isZero();
        assertThat(output)
                .contains("bootstrap=true")
                .contains("[ERROR]")
                .contains("error message")
                .contains("IllegalStateException: boom")
                .contains("example.error.logger - ");
    }

    private static Path javaExecutable(String toolName) {
        String executableName = isWindows() ? toolName + ".exe" : toolName;
        List<Path> candidates = List.of(
                Path.of(System.getenv().getOrDefault("JAVA_HOME", "")).resolve("bin").resolve(executableName),
                Path.of(System.getProperty("java.home")).resolve("bin").resolve(executableName),
                Path.of(System.getProperty("java.home")).resolve("..").normalize().resolve("bin").resolve(executableName)
        );

        return candidates.stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Unable to locate " + executableName));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
    }

    private static String readProcessOutput(Process process) throws IOException {
        try (InputStream inputStream = process.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String readResource(String resourceName) throws IOException {
        try (InputStream inputStream = SimpleLog_1Test.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (inputStream == null) {
                throw new IllegalStateException("Missing resource: " + resourceName);
            }
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8).trim();
        }
    }
}
