/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_shared.maven_invoker;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationOutputHandler;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.InvokerLogger;
import org.apache.maven.shared.invoker.MavenCommandLineBuilder;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.apache.maven.shared.invoker.PrintStreamLogger;
import org.codehaus.plexus.util.cli.Commandline;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Maven_invokerTest {
    @TempDir
    Path tempDir;

    @Test
    void invocationRequestExposesDefaultsAndFluentConfiguration() {
        DefaultInvocationRequest defaults = new DefaultInvocationRequest();
        File defaultDirectory = tempDir.resolve("default-directory").toFile();
        ByteArrayInputStream defaultInput = new ByteArrayInputStream(new byte[0]);
        InvocationOutputHandler defaultHandler = line -> { };

        assertThat(defaults.isInteractive()).isFalse();
        assertThat(defaults.isOffline()).isFalse();
        assertThat(defaults.isUpdateSnapshots()).isFalse();
        assertThat(defaults.isRecursive()).isTrue();
        assertThat(defaults.isDebug()).isFalse();
        assertThat(defaults.isShowErrors()).isFalse();
        assertThat(defaults.isShellEnvironmentInherited()).isTrue();
        assertThat(defaults.isNonPluginUpdates()).isFalse();
        assertThat(defaults.getFailureBehavior()).isEqualTo(InvocationRequest.REACTOR_FAIL_FAST);
        assertThat(defaults.getBaseDirectory(defaultDirectory)).isSameAs(defaultDirectory);
        assertThat(defaults.getLocalRepositoryDirectory(defaultDirectory)).isSameAs(defaultDirectory);
        assertThat(defaults.getInputStream(defaultInput)).isSameAs(defaultInput);
        assertThat(defaults.getOutputHandler(defaultHandler)).isSameAs(defaultHandler);
        assertThat(defaults.getErrorHandler(defaultHandler)).isSameAs(defaultHandler);
        assertThat(defaults.getShellEnvironments()).isEmpty();

        File baseDirectory = tempDir.resolve("project").toFile();
        File pomFile = tempDir.resolve("project/custom-pom.xml").toFile();
        File localRepository = tempDir.resolve("repository").toFile();
        File javaHome = tempDir.resolve("java-home").toFile();
        File settings = tempDir.resolve("settings.xml").toFile();
        ByteArrayInputStream input = new ByteArrayInputStream("answer\n".getBytes(StandardCharsets.UTF_8));
        InvocationOutputHandler outputHandler = line -> { };
        InvocationOutputHandler errorHandler = line -> { };
        Properties properties = new Properties();
        properties.setProperty("skipTests", "true");
        List<String> goals = Arrays.asList("clean", "verify");
        List<String> profiles = Arrays.asList("native", "ci");

        InvocationRequest configured = new DefaultInvocationRequest()
                .setInteractive(true)
                .setOffline(true)
                .setUpdateSnapshots(true)
                .setRecursive(false)
                .setDebug(true)
                .setShowErrors(true)
                .setShellEnvironmentInherited(false)
                .setNonPluginUpdates(true)
                .setFailureBehavior(InvocationRequest.REACTOR_FAIL_NEVER)
                .setBaseDirectory(baseDirectory)
                .setPomFile(pomFile)
                .setPomFileName("custom-pom.xml")
                .setLocalRepositoryDirectory(localRepository)
                .setJavaHome(javaHome)
                .setUserSettingsFile(settings)
                .setGlobalChecksumPolicy(InvocationRequest.CHECKSUM_POLICY_WARN)
                .setInputStream(input)
                .setOutputHandler(outputHandler)
                .setErrorHandler(errorHandler)
                .setProperties(properties)
                .setGoals(goals)
                .setProfiles(profiles)
                .setMavenOpts("-Xmx64m");

        assertThat(configured.isInteractive()).isTrue();
        assertThat(configured.isOffline()).isTrue();
        assertThat(configured.isUpdateSnapshots()).isTrue();
        assertThat(configured.isRecursive()).isFalse();
        assertThat(configured.isDebug()).isTrue();
        assertThat(configured.isShowErrors()).isTrue();
        assertThat(configured.isShellEnvironmentInherited()).isFalse();
        assertThat(configured.isNonPluginUpdates()).isTrue();
        assertThat(configured.getFailureBehavior()).isEqualTo(InvocationRequest.REACTOR_FAIL_NEVER);
        assertThat(configured.getBaseDirectory()).isSameAs(baseDirectory);
        assertThat(configured.getBaseDirectory(defaultDirectory)).isSameAs(baseDirectory);
        assertThat(configured.getPomFile()).isSameAs(pomFile);
        assertThat(configured.getPomFileName()).isEqualTo("custom-pom.xml");
        assertThat(configured.getLocalRepositoryDirectory(defaultDirectory)).isSameAs(localRepository);
        assertThat(configured.getJavaHome()).isSameAs(javaHome);
        assertThat(configured.getUserSettingsFile()).isSameAs(settings);
        assertThat(configured.getGlobalChecksumPolicy()).isEqualTo(InvocationRequest.CHECKSUM_POLICY_WARN);
        assertThat(configured.getInputStream(defaultInput)).isSameAs(input);
        assertThat(configured.getOutputHandler(defaultHandler)).isSameAs(outputHandler);
        assertThat(configured.getErrorHandler(defaultHandler)).isSameAs(errorHandler);
        assertThat(configured.getProperties()).isSameAs(properties);
        assertThat(configured.getGoals()).isSameAs(goals);
        assertThat(configured.getProfiles()).isSameAs(profiles);
        assertThat(configured.getMavenOpts()).isEqualTo("-Xmx64m");
    }

    @Test
    void printStreamHandlerWritesLinesAndRejectsNullStream() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        FlushRecordingPrintStream stream = new FlushRecordingPrintStream(bytes);
        PrintStreamHandler handler = new PrintStreamHandler(stream, true);

        handler.consumeLine("first line");
        handler.consumeLine(null);
        handler.consumeLine("third line");

        assertThat(bytes.toString()).isEqualTo(String.join(System.lineSeparator(), "first line", "", "third line", ""));
        assertThat(stream.wasFlushed()).isTrue();
        assertThatExceptionOfType(NullPointerException.class)
                .isThrownBy(() -> new PrintStreamHandler(null, false))
                .withMessage("missing output stream");
    }

    @Test
    void printStreamLoggerFiltersByThresholdAndFormatsThrowable() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStreamLogger logger = new PrintStreamLogger(new PrintStream(bytes), InvokerLogger.WARN);

        logger.debug("debug message");
        logger.info("info message");
        logger.warn("warn message");
        logger.error("error message", new IllegalArgumentException("bad input"));
        logger.fatalError(null);

        String log = bytes.toString();
        assertThat(logger.getThreshold()).isEqualTo(InvokerLogger.WARN);
        assertThat(logger.isDebugEnabled()).isFalse();
        assertThat(logger.isInfoEnabled()).isFalse();
        assertThat(logger.isWarnEnabled()).isTrue();
        assertThat(logger.isErrorEnabled()).isTrue();
        assertThat(logger.isFatalErrorEnabled()).isTrue();
        assertThat(log).doesNotContain("debug message", "info message");
        assertThat(log).contains("[WARN] warn message");
        assertThat(log).contains("[ERROR] error message");
        assertThat(log).contains("Error:");
        assertThat(log).contains("java.lang.IllegalArgumentException: bad input");
        assertThat(log).doesNotContain("[FATAL]");

        logger.setThreshold(InvokerLogger.DEBUG);
        assertThat(logger.isDebugEnabled()).isTrue();
    }

    @Test
    void commandLineBuilderTranslatesRequestIntoMavenCommandLine() throws Exception {
        Path mavenHome = createFakeMavenHome(0);
        Path projectDirectory = Files.createDirectory(tempDir.resolve("project"));
        Path localRepository = Files.createDirectory(tempDir.resolve("local-repository"));
        Path javaHome = Files.createDirectory(tempDir.resolve("java-home"));
        Path settings = Files.writeString(tempDir.resolve("settings.xml"), "<settings/>\n");
        Properties properties = new Properties();
        properties.setProperty("invoker.test", "true");
        InvocationRequest request = new DefaultInvocationRequest()
                .setShellEnvironmentInherited(false)
                .setBaseDirectory(projectDirectory.toFile())
                .setPomFileName("module-pom.xml")
                .setLocalRepositoryDirectory(localRepository.toFile())
                .setJavaHome(javaHome.toFile())
                .setMavenOpts("-Xmx96m")
                .setUserSettingsFile(settings.toFile())
                .setProperties(properties)
                .setProfiles(Arrays.asList("ci", "native"))
                .setGoals(Arrays.asList("clean", "verify"))
                .setOffline(true)
                .setUpdateSnapshots(true)
                .setRecursive(false)
                .setDebug(true)
                .setShowErrors(true)
                .setFailureBehavior(InvocationRequest.REACTOR_FAIL_AT_END)
                .setGlobalChecksumPolicy(InvocationRequest.CHECKSUM_POLICY_FAIL)
                .setNonPluginUpdates(true);
        MavenCommandLineBuilder builder = new MavenCommandLineBuilder();
        builder.setMavenHome(mavenHome.toFile());

        Commandline commandline = builder.build(request);

        assertThat(commandline.getExecutable()).isEqualTo(mavenExecutable(mavenHome).toRealPath().toString());
        assertThat(commandline.getWorkingDirectory()).isEqualTo(projectDirectory.toRealPath().toFile());
        assertThat(commandline.getArguments()).contains(
                "-B",
                "-o",
                "-U",
                "-N",
                "-X",
                "-fae",
                "-C",
                "-npu",
                "-f",
                "module-pom.xml",
                "-s",
                settings.toRealPath().toString(),
                "-D",
                "invoker.test=true",
                "maven.repo.local=" + localRepository.toRealPath(),
                "-P",
                "ci,native",
                "clean",
                "verify");
        assertThat(commandline.getArguments()).doesNotContain("-e");
        assertThat(commandline.getEnvironmentVariables()).contains(
                "JAVA_HOME=" + javaHome.toAbsolutePath(),
                "MAVEN_OPTS=-Xmx96m");
    }

    @Test
    void commandLineBuilderRejectsMissingLocalRepositoryDirectory() throws Exception {
        MavenCommandLineBuilder builder = new MavenCommandLineBuilder();
        builder.setMavenHome(createFakeMavenHome(0).toFile());
        InvocationRequest request = new DefaultInvocationRequest()
                .setShellEnvironmentInherited(false)
                .setLocalRepositoryDirectory(tempDir.resolve("missing-repository").toFile());

        assertThatThrownBy(() -> builder.build(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is NOT a directory");
    }

    @Test
    void defaultInvokerForwardsInputStreamForInteractiveInvocation() throws Exception {
        Path mavenHome = createInputEchoingMavenHome();
        Path projectDirectory = Files.createDirectory(tempDir.resolve("interactive-project"));
        List<String> outputLines = new ArrayList<>();
        List<String> errorLines = new ArrayList<>();
        InvocationRequest request = new DefaultInvocationRequest()
                .setBaseDirectory(projectDirectory.toFile())
                .setInteractive(true)
                .setInputStream(new ByteArrayInputStream("provided-answer\n".getBytes(StandardCharsets.UTF_8)));
        Invoker invoker = new DefaultInvoker()
                .setMavenHome(mavenHome.toFile())
                .setOutputHandler(outputLines::add)
                .setErrorHandler(errorLines::add)
                .setLogger(new PrintStreamLogger(new PrintStream(new ByteArrayOutputStream()), InvokerLogger.ERROR));

        InvocationResult result = invoker.execute(request);

        assertThat(result.getExitCode()).isZero();
        assertThat(result.getExecutionException()).isNull();
        assertThat(outputLines).contains("STDIN=provided-answer");
        assertThat(errorLines).isEmpty();
    }

    @Test
    void defaultInvokerExecutesConfiguredMavenCommandAndCapturesResult() throws Exception {
        Path mavenHome = createFakeMavenHome(7);
        Path projectDirectory = Files.createDirectory(tempDir.resolve("invoked-project"));
        List<String> outputLines = new ArrayList<>();
        List<String> errorLines = new ArrayList<>();
        InvocationRequest request = new DefaultInvocationRequest()
                .setBaseDirectory(projectDirectory.toFile())
                .setGoals(Arrays.asList("validate", "test"))
                .setMavenOpts("-Xmx48m")
                .addShellEnvironment("INVOKER_TEST_ENV", "custom-value");
        Invoker invoker = new DefaultInvoker()
                .setMavenHome(mavenHome.toFile())
                .setOutputHandler(outputLines::add)
                .setErrorHandler(errorLines::add)
                .setLogger(new PrintStreamLogger(new PrintStream(new ByteArrayOutputStream()), InvokerLogger.ERROR));

        InvocationResult result = invoker.execute(request);

        assertThat(result.getExitCode()).isEqualTo(7);
        assertThat(result.getExecutionException()).isNull();
        assertThat(outputLines).contains(
                "PWD=" + projectDirectory.toRealPath(),
                "CUSTOM=custom-value",
                "MAVEN_OPTS=-Xmx48m");
        assertThat(outputLines).anySatisfy(line -> assertThat(line)
                .contains("ARGS=")
                .contains("validate")
                .contains("test"));
        assertThat(errorLines).contains("stderr-line");
    }

    @Test
    void defaultInvokerWrapsCommandLineConfigurationFailure() {
        DefaultInvoker invoker = new DefaultInvoker();
        invoker.setMavenHome(tempDir.resolve("missing-maven-home").toFile());
        InvocationRequest request = new DefaultInvocationRequest().setShellEnvironmentInherited(false);

        assertThatThrownBy(() -> invoker.execute(request))
                .isInstanceOf(MavenInvocationException.class)
                .hasMessageContaining("Error configuring command-line")
                .hasMessageContaining("Maven executable not found");
    }

    private Path createInputEchoingMavenHome() throws IOException {
        Path mavenHome = Files.createDirectory(tempDir.resolve("maven-home-input-" + System.nanoTime()));
        Path binDirectory = Files.createDirectory(mavenHome.resolve("bin"));
        Path executable = mavenExecutable(mavenHome);
        String script = isWindows()
                ? "@echo off\r\n"
                        + "set /p INVOKER_STDIN=\r\n"
                        + "echo STDIN=%INVOKER_STDIN%\r\n"
                        + "exit /B 0\r\n"
                : "#!/bin/sh\n"
                        + "IFS= read -r INVOKER_STDIN || true\n"
                        + "echo STDIN=$INVOKER_STDIN\n"
                        + "exit 0\n";
        Files.writeString(executable, script, StandardCharsets.UTF_8);
        executable.toFile().setExecutable(true);
        assertThat(binDirectory).isDirectory();
        return mavenHome;
    }

    private Path createFakeMavenHome(int exitCode) throws IOException {
        Path mavenHome = Files.createDirectory(tempDir.resolve("maven-home-" + exitCode + "-" + System.nanoTime()));
        Path binDirectory = Files.createDirectory(mavenHome.resolve("bin"));
        Path executable = mavenExecutable(mavenHome);
        String script = isWindows()
                ? "@echo off\r\n"
                        + "echo PWD=%CD%\r\n"
                        + "echo ARGS=%*\r\n"
                        + "echo CUSTOM=%INVOKER_TEST_ENV%\r\n"
                        + "echo MAVEN_OPTS=%MAVEN_OPTS%\r\n"
                        + "echo stderr-line 1>&2\r\n"
                        + "exit /B " + exitCode + "\r\n"
                : "#!/bin/sh\n"
                        + "echo PWD=$(pwd)\n"
                        + "echo ARGS=$*\n"
                        + "echo CUSTOM=$INVOKER_TEST_ENV\n"
                        + "echo MAVEN_OPTS=$MAVEN_OPTS\n"
                        + "echo stderr-line 1>&2\n"
                        + "exit " + exitCode + "\n";
        Files.writeString(executable, script, StandardCharsets.UTF_8);
        executable.toFile().setExecutable(true);
        assertThat(binDirectory).isDirectory();
        return mavenHome;
    }

    private static Path mavenExecutable(Path mavenHome) {
        return mavenHome.resolve("bin").resolve(isWindows() ? "mvn.bat" : "mvn");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase().contains("win");
    }

    private static final class FlushRecordingPrintStream extends PrintStream {
        private boolean flushed;

        private FlushRecordingPrintStream(ByteArrayOutputStream out) {
            super(out);
        }

        @Override
        public void flush() {
            flushed = true;
            super.flush();
        }

        private boolean wasFlushed() {
            return flushed;
        }
    }
}
