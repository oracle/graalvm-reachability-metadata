/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.ClassTemplate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ClassTemplateInvocationContext;
import org.junit.jupiter.api.extension.ClassTemplateInvocationContextProvider;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.context.aot.AbstractAotProcessor.Settings;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.aot.TestAotProcessor;

import static org.assertj.core.api.Assertions.assertThat;

@ClassTemplate
@ExtendWith(TestClassScannerTest.SingleInvocationClassTemplateProvider.class)
@ContextConfiguration(classes = TestClassScannerTest.TestConfiguration.class)
public class TestClassScannerTest {
    @Test
    void expandsJupiterClassTemplateWithNestedSpringTestClasses() {
        Path testClasses = Path.of("build", "classes", "java", "test").toAbsolutePath();
        ClasspathScanningProcessor processor = new ClasspathScanningProcessor(Set.of(testClasses));

        try {
            Set<Class<?>> scannedClasses = processor.scanTestClasses();

            assertThat(scannedClasses)
                    .contains(TestClassScannerTest.class, NestedSpringTestCase.class);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    @Nested
    class NestedSpringTestCase {
        @Test
        void nestedTest() {
            assertThat(true).isTrue();
        }
    }

    static class TestConfiguration {
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
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
                @Override
                public String getDisplayName(int invocationIndex) {
                    return "single invocation";
                }
            });
        }
    }

    static class ClasspathScanningProcessor extends TestAotProcessor {
        ClasspathScanningProcessor(Set<Path> classpathRoots) {
            super(classpathRoots, Settings.builder()
                    .sourceOutput(Path.of("build", "generated", "test-class-scanner", "sources"))
                    .resourceOutput(Path.of("build", "generated", "test-class-scanner",
                            "resources"))
                    .classOutput(Path.of("build", "generated", "test-class-scanner", "classes"))
                    .groupId("org_springframework")
                    .artifactId("spring_test")
                    .build());
        }

        Set<Class<?>> scanTestClasses() {
            return scanClasspathRoots().collect(Collectors.toSet());
        }
    }
}
