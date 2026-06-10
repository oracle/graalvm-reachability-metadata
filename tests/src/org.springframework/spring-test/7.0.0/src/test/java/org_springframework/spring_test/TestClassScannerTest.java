/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

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
    void includesNestedClassesDeclaredInJupiterClassTemplateTests(@TempDir Path tempDir) throws Exception {
        Path classpathRoot = tempDir.resolve("scanner-classes");
        copyClassResource(TestClassScannerTest.class, classpathRoot);
        copyClassResource(ParameterizedSpringContextTest.class, classpathRoot);
        copyClassResource(ParameterizedSpringContextTest.NestedSpringContextTest.class, classpathRoot);

        Set<Class<?>> scannedClasses;
        try {
            scannedClasses = new ScanningProcessor(classpathRoot, tempDir).scanClasses();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        }

        assertThat(scannedClasses)
                .contains(ParameterizedSpringContextTest.class,
                        ParameterizedSpringContextTest.NestedSpringContextTest.class);
    }

    private static void copyClassResource(Class<?> sourceClass, Path classpathRoot) throws IOException {
        String resourceName = sourceClass.getName().replace('.', '/') + ".class";
        Path target = classpathRoot.resolve(resourceName);
        Files.createDirectories(target.getParent());
        try (InputStream inputStream = sourceClass.getClassLoader().getResourceAsStream(resourceName)) {
            assertThat(inputStream).as(resourceName).isNotNull();
            Files.copy(inputStream, target);
        }
    }

    private static final class ScanningProcessor extends TestAotProcessor {

        ScanningProcessor(Path classpathRoot, Path outputRoot) {
            super(Set.of(classpathRoot), Settings.builder()
                    .sourceOutput(outputRoot.resolve("generated-sources"))
                    .resourceOutput(outputRoot.resolve("generated-resources"))
                    .classOutput(outputRoot.resolve("generated-classes"))
                    .groupId("org_springframework")
                    .artifactId("spring_test")
                    .build());
        }

        Set<Class<?>> scanClasses() {
            return scanClasspathRoots().collect(Collectors.toSet());
        }
    }

    @ParameterizedClass
    @ValueSource(strings = "class-template")
    @ContextConfiguration(classes = TestConfiguration.class)
    static class ParameterizedSpringContextTest {

        private final String value;

        ParameterizedSpringContextTest(String value) {
            this.value = value;
        }

        @Test
        void hasParameterizedValue() {
            assertThat(this.value).isEqualTo("class-template");
        }

        @Nested
        class NestedSpringContextTest {

            @Test
            void hasAccessToOuterClassTemplateInvocation() {
                assertThat(ParameterizedSpringContextTest.this.value).isEqualTo("class-template");
            }
        }
    }

    static class TestConfiguration {
    }
}
