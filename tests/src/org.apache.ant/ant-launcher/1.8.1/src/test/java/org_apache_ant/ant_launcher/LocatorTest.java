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
import java.net.URL;
import java.net.URLClassLoader;
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

import org.apache.tools.ant.launch.Locator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class LocatorTest {
    private static final String TEST_RESOURCE = "org/example/locator-resource.txt";
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(30);

    @TempDir
    Path tempDir;

    @Test
    void locatesResourceDirectoryWithProvidedClassLoader() throws Exception {
        Path resourceFile = tempDir.resolve(TEST_RESOURCE);
        Files.createDirectories(resourceFile.getParent());
        Files.write(resourceFile, List.of("locator resource"), StandardCharsets.UTF_8);

        try (URLClassLoader classLoader = new URLClassLoader(new URL[] {tempDir.toUri().toURL()}, null)) {
            File source = Locator.getResourceSource(classLoader, TEST_RESOURCE);

            assertThat(source).isNotNull();
            assertThat(source.getCanonicalFile()).isEqualTo(tempDir.toFile().getCanonicalFile());
        }
    }

    @Test
    void usesDefaultClassLoaderWhenNoClassLoaderIsProvided() {
        File source = Locator.getResourceSource(null, "org/example/missing-locator-resource.txt");

        assertThat(source).isNull();
    }

    @Test
    void locatesResourceThroughBootstrapClassLoader() throws Exception {
        Path antLauncherJar = locateAntLauncherJar();
        Path probeSource = writeBootstrapProbeSource();

        List<String> command = new ArrayList<>();
        command.add(javaExecutable().toString());
        jacocoAgentArgument().ifPresent(command::add);
        command.add("-Xbootclasspath/a:" + antLauncherJar);
        command.add("-cp");
        command.add(antLauncherJar.toString());
        command.add(probeSource.toString());

        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        boolean finished = process.waitFor(PROCESS_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

        assertThat(finished).as("Bootstrap probe output:%n%s", output).isTrue();
        assertThat(process.exitValue()).as("Bootstrap probe output:%n%s", output).isZero();
    }

    private Path writeBootstrapProbeSource() throws IOException {
        Path probeSource = tempDir.resolve("LocatorBootstrapProbe.java");
        Files.writeString(probeSource, """
                import java.io.File;
                import org.apache.tools.ant.launch.Locator;

                public class LocatorBootstrapProbe {
                    public static void main(String[] args) {
                        if (Locator.class.getClassLoader() != null) {
                            throw new IllegalStateException(
                                    "Locator was not loaded by the bootstrap class loader");
                        }

                        File source = Locator.getResourceSource(
                                null, "org/apache/tools/ant/launch/Locator.class");
                        if (source == null) {
                            throw new IllegalStateException(
                                    "Locator source was not found through the system resource lookup");
                        }
                        String name = source.getName();
                        if (!name.startsWith("ant-launcher-") || !name.endsWith(".jar")) {
                            throw new IllegalStateException("Unexpected Locator source: " + source);
                        }
                    }
                }
                """, StandardCharsets.UTF_8);
        return probeSource;
    }

    private static Path javaExecutable() {
        String executableName = System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java";
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
        CodeSource codeSource = Locator.class.getProtectionDomain().getCodeSource();
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
        throw new IllegalStateException("Could not locate the ant-launcher jar on the test class path");
    }

    private static Optional<Path> findAntLauncherJar(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            return Optional.empty();
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            return paths
                    .filter(LocatorTest::isAntLauncherJar)
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
