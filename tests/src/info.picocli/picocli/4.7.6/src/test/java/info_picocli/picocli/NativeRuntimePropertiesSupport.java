/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

final class NativeRuntimePropertiesSupport {
    private NativeRuntimePropertiesSupport() {
    }

    static void restoreMissingRuntimeProperties() {
        Properties embeddedRuntimeProperties = loadEmbeddedRuntimeProperties();

        if (System.getProperty("java.home") == null || System.getProperty("java.home").isBlank()) {
            String javaHome = firstNonBlank(
                    embeddedRuntimeProperties.getProperty("java.home"),
                    System.getenv("JAVA_HOME"),
                    System.getenv("GRAALVM_HOME"));
            if (!javaHome.isBlank()) {
                System.setProperty("java.home", javaHome);
            }
        }

        String currentClassPath = System.getProperty("java.class.path", "");
        if (currentClassPath.isBlank()) {
            String classPath = embeddedRuntimeProperties.getProperty("java.class.path", "");
            if (!classPath.isBlank()) {
                System.setProperty("java.class.path", classPath);
            }
        }
    }

    private static Properties loadEmbeddedRuntimeProperties() {
        Properties properties = new Properties();
        try (InputStream inputStream = NativeRuntimePropertiesSupport.class.getResourceAsStream("/native-runtime.properties")) {
            if (inputStream != null) {
                properties.load(inputStream);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not load embedded native runtime properties", exception);
        }
        return properties;
    }

    private static String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }
}
