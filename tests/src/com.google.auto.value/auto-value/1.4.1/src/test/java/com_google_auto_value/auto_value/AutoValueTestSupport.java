/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_auto_value.auto_value;

import com.google.auto.value.AutoValue;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

final class AutoValueTestSupport {

    private static final String AUTO_VALUE_COORDINATE_PATH = "com/google/auto/value/auto-value";
    private static final String AUTO_VALUE_JAR_PREFIX = "auto-value-";

    private AutoValueTestSupport() {
    }

    static void ensureJavaHomeProperty() {
        if (System.getProperty("java.home") != null) {
            return;
        }
        firstNonBlank(System.getenv("JAVA_HOME"), System.getenv("GRAALVM_HOME"))
                .ifPresent(javaHome -> System.setProperty("java.home", javaHome));
    }

    static String javaClassPath() {
        String classPath = System.getProperty("java.class.path", "");
        if (containsAutoValueJar(classPath)) {
            return classPath;
        }
        List<String> entries = new ArrayList<>();
        for (String entry : classPath.split(File.pathSeparator)) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        entries.add(autoValueJarPath().toString());
        String fallbackClassPath = String.join(File.pathSeparator, entries.stream().distinct().toList());
        System.setProperty("java.class.path", fallbackClassPath);
        return fallbackClassPath;
    }

    static List<Path> javaClassPathEntries() {
        List<Path> entries = new ArrayList<>();
        for (String entry : javaClassPath().split(File.pathSeparator)) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                entries.add(Path.of(trimmed));
            }
        }
        return entries;
    }

    static URL autoValueJarUrl() throws IOException {
        CodeSource codeSource = AutoValue.class.getProtectionDomain().getCodeSource();
        if (codeSource != null && codeSource.getLocation() != null) {
            URL location = codeSource.getLocation();
            if (isJarFile(location)) {
                return location;
            }
        }
        return autoValueJarPath().toUri().toURL();
    }

    private static Path autoValueJarPath() {
        String implementationVersion = AutoValue.class.getPackage().getImplementationVersion();
        List<Path> repositoryDirectories = List.of(
                Path.of(System.getProperty("user.home"), ".gradle", "caches", "modules-2", "files-2.1",
                        "com.google.auto.value", "auto-value"),
                Path.of(System.getProperty("user.home"), ".m2", "repository").resolve(AUTO_VALUE_COORDINATE_PATH));
        for (Path repositoryDirectory : repositoryDirectories) {
            Optional<Path> jar = findAutoValueJar(repositoryDirectory, implementationVersion);
            if (jar.isPresent()) {
                return jar.get();
            }
        }
        throw new AssertionError("Could not locate the AutoValue jar on the test class path");
    }

    private static Optional<Path> findAutoValueJar(Path repositoryDirectory, String implementationVersion) {
        if (!Files.isDirectory(repositoryDirectory)) {
            return Optional.empty();
        }
        try (Stream<Path> paths = Files.walk(repositoryDirectory)) {
            return paths
                    .filter(Files::isRegularFile)
                    .filter(path -> matchesAutoValueJar(path, implementationVersion))
                    .findFirst();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static boolean matchesAutoValueJar(Path path, String implementationVersion) {
        String fileName = path.getFileName().toString();
        if (implementationVersion != null) {
            return fileName.equals(AUTO_VALUE_JAR_PREFIX + implementationVersion + ".jar");
        }
        return fileName.startsWith(AUTO_VALUE_JAR_PREFIX) && fileName.endsWith(".jar");
    }

    private static boolean containsAutoValueJar(String classPath) {
        if (classPath.isBlank()) {
            return false;
        }
        for (String entry : classPath.split(File.pathSeparator)) {
            String trimmed = entry.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            Path path = Path.of(trimmed);
            Path fileName = path.getFileName();
            if (fileName != null && matchesAutoValueJar(path, AutoValue.class.getPackage().getImplementationVersion())) {
                return true;
            }
        }
        return false;
    }

    private static Optional<String> firstNonBlank(String... values) {
        return Stream.of(values)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .findFirst();
    }

    private static boolean isJarFile(URL location) {
        if (!"file".equals(location.getProtocol())) {
            return false;
        }
        try {
            Path path = Path.of(location.toURI());
            return Files.isRegularFile(path) && path.getFileName().toString().endsWith(".jar");
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return false;
        }
    }
}
