/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.context.aot.AbstractAotProcessor.Settings;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.aot.TestAotProcessor;

import static org.assertj.core.api.Assertions.assertThat;

public class TestClassScannerTest {
    @Test
    void scansNestedClassesDeclaredInJupiterClassTemplateSpringTests(@TempDir Path output) throws Exception {
        try {
            Set<Class<?>> scannedClasses = new ScannerProcessor(Set.of(testClasspathRoot()), output).scanTestClasses();

            assertThat(scannedClasses)
                    .contains(ClassTemplateSpringCase.class, ClassTemplateSpringCase.NestedSpringCase.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static Path testClasspathRoot() throws URISyntaxException {
        Path compiledTestClasses = Paths.get("build", "classes", "java", "test").toAbsolutePath().normalize();
        if (Files.exists(compiledTestClasses)) {
            return compiledTestClasses;
        }
        CodeSource codeSource = TestClassScannerTest.class.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return Paths.get(codeSource.getLocation().toURI());
    }

    static class ScannerProcessor extends TestAotProcessor {
        ScannerProcessor(Set<Path> classpathRoots, Path output) {
            super(classpathRoots, Settings.builder()
                    .sourceOutput(output.resolve("sources"))
                    .resourceOutput(output.resolve("resources"))
                    .classOutput(output.resolve("classes"))
                    .groupId("org.example")
                    .artifactId("scanner")
                    .build());
        }

        Set<Class<?>> scanTestClasses() {
            try (Stream<Class<?>> stream = scanClasspathRoots()) {
                return stream.collect(Collectors.toCollection(LinkedHashSet::new));
            }
        }
    }

    @ParameterizedClass
    @ValueSource(strings = "sample")
    @ContextConfiguration(classes = SpringTestConfiguration.class)
    static class ClassTemplateSpringCase {
        ClassTemplateSpringCase(String value) {
        }

        @Test
        void test() {
        }

        @Nested
        class NestedSpringCase {
            @Test
            void test() {
            }
        }
    }

    static class SpringTestConfiguration {
    }
}
