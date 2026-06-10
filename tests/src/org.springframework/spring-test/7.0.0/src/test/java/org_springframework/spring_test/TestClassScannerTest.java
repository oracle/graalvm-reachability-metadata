/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.aot.AbstractAotProcessor.Settings;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.aot.TestAotProcessor;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ParameterizedClass
@ValueSource(strings = "test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestClassScannerTest.Config.class)
public class TestClassScannerTest {
    @Parameter
    String value;

    @Test
    void scanClasspathRootsExpandsSpringClassTemplatesToNestedTestClasses(
            @TempDir Path outputDirectory) throws Exception {
        Assumptions.assumeFalse(isNativeImageRuntime(),
                "TestClassScanner scans JVM classpath roots, which native tests expose as the executable path");
        ScanOnlyTestAotProcessor processor =
                new ScanOnlyTestAotProcessor(classpathRoots(), settings(outputDirectory));

        try {
            List<Class<?>> scannedClasses = processor.scanClasses();

            assertThat(scannedClasses)
                    .contains(TestClassScannerTest.class,
                            TestClassScannerTest.NestedSpringTestCase.class,
                            TestClassScannerTest.NestedSpringTestCase
                                    .DeepNestedSpringTestCase.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Test
    void testMethod() {
        assertThat(this.value).isEqualTo("test");
    }

    private static Set<Path> classpathRoots() throws Exception {
        URL location = TestClassScannerTest.class.getProtectionDomain()
                .getCodeSource().getLocation();
        Path classpathRoot = Path.of(location.toURI());
        assertThat(Files.exists(classpathRoot)).isTrue();
        return Set.of(classpathRoot);
    }

    private static Settings settings(Path outputDirectory) {
        Path generatedSources = outputDirectory.resolve("generated-sources");
        Path generatedResources = outputDirectory.resolve("generated-resources");
        Path generatedClasses = outputDirectory.resolve("generated-classes");
        return Settings.builder()
                .sourceOutput(generatedSources)
                .resourceOutput(generatedResources)
                .classOutput(generatedClasses)
                .groupId("org.example")
                .artifactId("test-class-scanner")
                .build();
    }

    private static boolean isNativeImageRuntime() {
        return "runtime".equals(System.getProperty("org.graalvm.nativeimage.imagecode"));
    }

    static class ScanOnlyTestAotProcessor extends TestAotProcessor {
        ScanOnlyTestAotProcessor(Set<Path> classpathRoots, Settings settings) {
            super(classpathRoots, settings);
        }

        List<Class<?>> scanClasses() {
            try (Stream<Class<?>> stream = scanClasspathRoots()) {
                return stream.toList();
            }
        }

        @Override
        protected Void doProcess() {
            throw new UnsupportedOperationException("Only classpath scanning is exercised");
        }
    }

    @Nested
    class NestedSpringTestCase {
        @Test
        void nestedTestMethod() {
            assertThat(value).isEqualTo("test");
        }

        @Nested
        class DeepNestedSpringTestCase {
            @Test
            void deepNestedTestMethod() {
                assertThat(value).isEqualTo("test");
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class Config {
    }
}
