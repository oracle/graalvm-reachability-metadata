/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import junit.runner.LoadingTestCollector;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class LoadingTestCollectorTest {
    private static final String CONSTRUCTOR_FIXTURE = "junit.junit.LoadingConstructorFixture";
    private static final String SUITE_FIXTURE = "junit.junit.LoadingSuiteFixture";

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    void discoversTestsWithSupportedJUnitConventions(@TempDir Path classPathRoot) throws IOException {
        Path packageDirectory = classPathRoot.resolve("junit/junit");
        Files.createDirectories(packageDirectory);
        copyClassFile("LoadingConstructorFixture.class", packageDirectory);
        copyClassFile("LoadingSuiteFixture.class", packageDirectory);

        String originalClassPath = System.getProperty("java.class.path");
        System.setProperty("java.class.path", classPathRoot.toString());
        try {
            LoadingTestCollector collector = new LoadingTestCollector();
            try {
                List<String> tests = collect(collector.collectTests());

                assertThat(tests).containsExactlyInAnyOrder(CONSTRUCTOR_FIXTURE, SUITE_FIXTURE);
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            }
        } finally {
            if (originalClassPath == null) {
                System.clearProperty("java.class.path");
            } else {
                System.setProperty("java.class.path", originalClassPath);
            }
        }
    }

    private static List<String> collect(Enumeration<?> tests) {
        List<String> names = new ArrayList<>();
        while (tests.hasMoreElements()) {
            names.add((String) tests.nextElement());
        }
        return names;
    }

    private static void copyClassFile(String resourceName, Path packageDirectory) throws IOException {
        InputStream resource = LoadingTestCollectorTest.class.getResourceAsStream(resourceName);
        assertThat(resource).isNotNull();
        try (InputStream input = resource) {
            Files.copy(input, packageDirectory.resolve(resourceName));
        }
    }
}
