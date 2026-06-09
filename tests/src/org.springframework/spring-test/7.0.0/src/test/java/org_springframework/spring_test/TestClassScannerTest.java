/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.net.URI;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.Parameter;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.context.aot.AbstractAotProcessor.Settings;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.aot.TestAotProcessor;

import static org.assertj.core.api.Assertions.assertThat;

@ParameterizedClass
@ValueSource(strings = "scanner")
@ContextConfiguration(classes = TestClassScannerTest.TestConfiguration.class)
public class TestClassScannerTest {
    @Parameter
    String value;

    @TempDir
    Path outputDirectory;

    @Test
    void discoversNestedTestsDeclaredInParameterizedSpringTestClass() throws Exception {
        Path classpathRoot = testClassesRoot();
        Settings settings = Settings.builder()
                .sourceOutput(this.outputDirectory.resolve("sources"))
                .resourceOutput(this.outputDirectory.resolve("resources"))
                .classOutput(this.outputDirectory.resolve("classes"))
                .groupId("org.example")
                .artifactId("spring-test-scanner")
                .build();
        ScanningTestAotProcessor processor =
                new ScanningTestAotProcessor(Set.of(classpathRoot), settings);

        List<Class<?>> scannedClasses;
        try (Stream<Class<?>> stream = processor.scanTestClasses()) {
            scannedClasses = stream.toList();
        }

        assertThat(this.value).isEqualTo("scanner");
        assertThat(scannedClasses).contains(TestClassScannerTest.class, NestedSpringTestCase.class);
    }

    private static Path testClassesRoot() throws Exception {
        CodeSource codeSource = TestClassScannerTest.class.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        URI location = codeSource.getLocation().toURI();
        return Path.of(location);
    }

    @Nested
    class NestedSpringTestCase {
        @Test
        void nestedTestMethod() {
            assertThat(value).isEqualTo("scanner");
        }
    }

    static class TestConfiguration {
    }

    static class ScanningTestAotProcessor extends TestAotProcessor {
        ScanningTestAotProcessor(Set<Path> classpathRoots, Settings settings) {
            super(classpathRoots, settings);
        }

        Stream<Class<?>> scanTestClasses() {
            return scanClasspathRoots();
        }

        @Override
        protected Void doProcess() {
            throw new UnsupportedOperationException("Use scanTestClasses() instead");
        }
    }
}
