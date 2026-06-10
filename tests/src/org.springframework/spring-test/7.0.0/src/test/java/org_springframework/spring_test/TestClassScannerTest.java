/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.ClassTemplate;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ClassTemplateInvocationContext;
import org.junit.jupiter.api.extension.ClassTemplateInvocationContextProvider;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.context.aot.AbstractAotProcessor.Settings;
import org.springframework.test.context.aot.TestAotProcessor;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class TestClassScannerTest {
    @TempDir
    Path outputDirectory;

    @Test
    void discoversNestedJupiterClassTemplateTestClasses() {
        try {
            Path testClassesRoot = Path.of("build", "classes", "java", "test")
                    .toAbsolutePath()
                    .normalize();
            ScanningTestAotProcessor processor = new ScanningTestAotProcessor(
                    testClassesRoot, this.outputDirectory);

            List<Class<?>> testClasses = processor.scanTestClasses();
            Class<?> deepNestedTestClass =
                    ClassTemplateSpringTestCase.NestedSpringTestCase.DeepNestedSpringTestCase.class;

            assertThat(testClasses)
                    .contains(ClassTemplateSpringTestCase.class)
                    .contains(ClassTemplateSpringTestCase.NestedSpringTestCase.class)
                    .contains(deepNestedTestClass);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class ScanningTestAotProcessor extends TestAotProcessor {
        private ScanningTestAotProcessor(Path classpathRoot, Path outputDirectory) {
            super(Set.of(classpathRoot), Settings.builder()
                    .sourceOutput(outputDirectory.resolve("sources"))
                    .resourceOutput(outputDirectory.resolve("resources"))
                    .classOutput(outputDirectory.resolve("classes"))
                    .groupId("org.example")
                    .artifactId("spring-test-scanner")
                    .build());
        }

        private List<Class<?>> scanTestClasses() {
            try (Stream<Class<?>> stream = scanClasspathRoots()) {
                return stream.toList();
            }
        }
    }

    @Disabled("Fixture discovered by TestClassScannerTest")
    @ClassTemplate
    @ExtendWith({SpringExtension.class,
            TestClassScannerTest.SingleInvocationClassTemplateProvider.class})
    static class ClassTemplateSpringTestCase {
        @Test
        void sampleTest() {
        }

        @Nested
        class NestedSpringTestCase {
            @Test
            void nestedTest() {
            }

            @Nested
            class DeepNestedSpringTestCase {
                @Test
                void deepNestedTest() {
                }
            }
        }
    }

    public static class SingleInvocationClassTemplateProvider
            implements ClassTemplateInvocationContextProvider {
        @Override
        public boolean supportsClassTemplate(ExtensionContext context) {
            return true;
        }

        @Override
        public Stream<ClassTemplateInvocationContext> provideClassTemplateInvocationContexts(
                ExtensionContext context) {

            return Stream.of(new ClassTemplateInvocationContext() {
            });
        }
    }
}
