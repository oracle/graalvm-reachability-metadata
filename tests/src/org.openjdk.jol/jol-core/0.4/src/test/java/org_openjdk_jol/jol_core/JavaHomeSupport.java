/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjdk_jol.jol_core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

final class JavaHomeSupport {
    private static final String TEST_RUNTIME_CLASSPATH_RESOURCE = "test-runtime-classpath.txt";

    private JavaHomeSupport() {
    }

    static String effectiveJavaHome(String configuredJavaHome) {
        return firstNonBlank(configuredJavaHome, System.getenv("JAVA_HOME"), System.getenv("GRAALVM_HOME"));
    }

    static String javaExecutable() {
        String javaHome = effectiveJavaHome(System.getProperty("java.home"));
        if (javaHome == null) {
            return executableName("java");
        }
        return Path.of(javaHome, "bin", executableName("java")).toString();
    }

    static String testRuntimeClassPath() throws IOException {
        String runtimeClassPath = System.getProperty("java.class.path");
        if (runtimeClassPath != null && !runtimeClassPath.isBlank()) {
            return runtimeClassPath;
        }

        try (InputStream input = JavaHomeSupport.class.getClassLoader()
                .getResourceAsStream(TEST_RUNTIME_CLASSPATH_RESOURCE)) {
            if (input != null) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        }

        Path generatedClassPathFile = Path.of("build", "generated", "test-runtime-classpath",
                TEST_RUNTIME_CLASSPATH_RESOURCE).toAbsolutePath();
        if (Files.isRegularFile(generatedClassPathFile)) {
            return Files.readString(generatedClassPathFile, StandardCharsets.UTF_8).trim();
        }

        Path fallbackClassesDirectory = Path.of("build", "classes", "java", "test").toAbsolutePath();
        if (Files.isDirectory(fallbackClassesDirectory)) {
            return fallbackClassesDirectory.toString();
        }
        return "";
    }

    static InputStream openTestClassResource(Class<?> type) throws IOException {
        String resourceName = type.getName().replace('.', '/') + ".class";
        InputStream input = type.getClassLoader().getResourceAsStream(resourceName);
        if (input != null) {
            return input;
        }

        Path classFile = Path.of("build", "classes", "java", "test", resourceName).toAbsolutePath();
        if (Files.isRegularFile(classFile)) {
            return Files.newInputStream(classFile);
        }
        return null;
    }

    static void restoreProperty(String key, String value) {
        if (value != null) {
            System.setProperty(key, value);
        } else {
            System.clearProperty(key);
        }
    }

    private static String executableName(String executable) {
        String operatingSystem = System.getProperty("os.name", "");
        if (operatingSystem.regionMatches(true, 0, "Windows", 0, "Windows".length())) {
            return executable + ".exe";
        }
        return executable;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
