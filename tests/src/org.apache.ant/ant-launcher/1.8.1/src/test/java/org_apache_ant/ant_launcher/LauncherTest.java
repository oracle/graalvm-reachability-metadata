/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ant.ant_launcher;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.apache.tools.ant.launch.Launcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LauncherTest {
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(30);

    @TempDir
    Path tempDir;

    @Test
    void launchesConfiguredAntMainClass() throws Exception {
        Path antLauncherJar = locateAntLauncherJar();
        Path classesDirectory = tempDir.resolve("classes");
        Files.createDirectories(classesDirectory);
        compileProbe(antLauncherJar, classesDirectory);

        List<String> command = new ArrayList<>();
        command.add(javaTool("java").toString());
        jacocoAgentArgument().ifPresent(command::add);
        command.add("-cp");
        command.add(classesDirectory + File.pathSeparator + antLauncherJar);
        command.add("LauncherProbe");
        command.add(tempDir.toString());

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(finished).as("Launcher probe output:%n%s", output).isTrue();
        assertThat(process.exitValue()).as("Launcher probe output:%n%s", output).isZero();
        assertThat(output).contains("LauncherProbe started");
    }

    private void compileProbe(Path antLauncherJar, Path classesDirectory) throws IOException, InterruptedException {
        Path probeSource = writeProbeSource();
        List<String> command = List.of(
                javaTool("javac").toString(),
                "-cp",
                antLauncherJar.toString(),
                "-d",
                classesDirectory.toString(),
                probeSource.toString());

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(finished).as("Launcher probe compilation output:%n%s", output).isTrue();
        assertThat(process.exitValue()).as("Launcher probe compilation output:%n%s", output).isZero();
    }

    private Path writeProbeSource() throws IOException {
        Path probeSource = tempDir.resolve("LauncherProbe.java");
        Files.writeString(probeSource, """
                import java.util.Arrays;
                import java.util.List;
                import java.util.Properties;
                import org.apache.tools.ant.launch.AntMain;
                import org.apache.tools.ant.launch.Launcher;

                public class LauncherProbe implements AntMain {
                    private static boolean started;
                    private static List<String> arguments = List.of();
                    private static Properties properties;
                    private static ClassLoader coreLoader;

                    public static void main(String[] args) {
                        System.setProperty(Launcher.ANTHOME_PROPERTY, args[0]);
                        System.setProperty(Launcher.ANTLIBDIR_PROPERTY, args[0]);

                        Launcher.main(new String[] {
                                "--nouserlib",
                                "--noclasspath",
                                "-main",
                                LauncherProbe.class.getName(),
                                "first-target",
                                "second-target"
                        });

                        if (!started) {
                            throw new IllegalStateException("Launcher did not call startAnt");
                        }
                        if (!arguments.equals(List.of("first-target", "second-target"))) {
                            throw new IllegalStateException("Unexpected arguments: " + arguments);
                        }
                        if (properties != null) {
                            throw new IllegalStateException("Unexpected properties: " + properties);
                        }
                        if (coreLoader != null) {
                            throw new IllegalStateException("Unexpected core loader: " + coreLoader);
                        }
                        System.out.println("LauncherProbe started");
                    }

                    @Override
                    public void startAnt(String[] args, Properties additionalUserProperties,
                            ClassLoader coreLoader) {
                        started = true;
                        arguments = Arrays.asList(args);
                        properties = additionalUserProperties;
                        LauncherProbe.coreLoader = coreLoader;
                    }
                }
                """, StandardCharsets.UTF_8);
        return probeSource;
    }

    private static Path javaTool(String toolName) {
        String executableName = System.getProperty("os.name").toLowerCase().contains("win")
                ? toolName + ".exe"
                : toolName;
        String javaHome = System.getProperty("java.home");
        if (javaHome == null || javaHome.isBlank()) {
            javaHome = System.getenv("JAVA_HOME");
        }
        if (javaHome == null || javaHome.isBlank()) {
            throw new IllegalStateException("Could not locate " + executableName + " because java.home is not set");
        }
        return Paths.get(javaHome, "bin", executableName);
    }

    private static Path locateAntLauncherJar() throws URISyntaxException, IOException {
        CodeSource codeSource = Launcher.class.getProtectionDomain().getCodeSource();
        if (codeSource != null && "file".equals(codeSource.getLocation().getProtocol())) {
            Path location = Paths.get(codeSource.getLocation().toURI());
            if (isAntLauncherJar(location)) {
                return location;
            }
        }

        String classPath = System.getProperty("java.class.path", "");
        for (String entry : classPath.split(File.pathSeparator)) {
            if (!entry.isEmpty()) {
                Path path = Paths.get(entry);
                if (isAntLauncherJar(path)) {
                    return path;
                }
            }
        }

        Path gradleCache = Paths.get(System.getProperty("user.home"),
                ".gradle", "caches", "modules-2", "files-2.1", "org.apache.ant", "ant-launcher");
        Optional<String> testedVersion = testedLibraryVersion();
        if (testedVersion.isPresent()) {
            Optional<Path> jar = findAntLauncherJar(gradleCache.resolve(testedVersion.get()));
            if (jar.isPresent()) {
                return jar.get();
            }
        }
        Optional<Path> jar = findAntLauncherJar(gradleCache);
        if (jar.isPresent()) {
            return jar.get();
        }
        throw new IllegalStateException("Could not locate the ant-launcher jar");
    }

    private static Optional<Path> findAntLauncherJar(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(LauncherTest::isAntLauncherJar)
                    .findFirst();
        }
    }

    private static Optional<String> testedLibraryVersion() throws IOException {
        Path propertiesFile = Paths.get("gradle.properties");
        if (!Files.isRegularFile(propertiesFile)) {
            return Optional.empty();
        }
        Properties properties = new Properties();
        try (InputStream inputStream = Files.newInputStream(propertiesFile)) {
            properties.load(inputStream);
        }
        return Optional.ofNullable(properties.getProperty("library.version"));
    }

    private static boolean isAntLauncherJar(Path path) {
        Path fileName = path.getFileName();
        return fileName != null
                && fileName.toString().startsWith("ant-launcher-")
                && fileName.toString().endsWith(".jar");
    }

    private static Optional<String> jacocoAgentArgument() {
        for (String argument : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
            if (argument.startsWith("-javaagent:") && argument.contains("jacoco")) {
                return Optional.of(withBootstrapInstrumentation(argument));
            }
        }
        return Optional.empty();
    }

    private static String withBootstrapInstrumentation(String argument) {
        int optionStart = argument.indexOf('=');
        if (optionStart < 0) {
            return argument + "=append=true,inclbootstrapclasses=true,includes=org.apache.tools.ant.launch.*";
        }
        String agentPath = argument.substring(0, optionStart);
        String options = argument.substring(optionStart + 1);
        List<String> rewrittenOptions = new ArrayList<>();
        boolean hasAppend = false;
        boolean hasBootstrap = false;
        boolean hasIncludes = false;
        for (String option : options.split(",")) {
            if (option.startsWith("append=")) {
                rewrittenOptions.add("append=true");
                hasAppend = true;
            } else if (option.startsWith("inclbootstrapclasses=")) {
                rewrittenOptions.add("inclbootstrapclasses=true");
                hasBootstrap = true;
            } else if (option.startsWith("includes=")) {
                rewrittenOptions.add("includes=org.apache.tools.ant.launch.*");
                hasIncludes = true;
            } else if (!option.isEmpty()) {
                rewrittenOptions.add(option);
            }
        }
        if (!hasAppend) {
            rewrittenOptions.add("append=true");
        }
        if (!hasBootstrap) {
            rewrittenOptions.add("inclbootstrapclasses=true");
        }
        if (!hasIncludes) {
            rewrittenOptions.add("includes=org.apache.tools.ant.launch.*");
        }
        return agentPath + "=" + String.join(",", rewrittenOptions);
    }
}
