/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.context.aot.AbstractAotProcessor.Settings;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.aot.TestAotProcessor;

import static org.assertj.core.api.Assertions.assertThat;

@ClassTemplate
@ExtendWith(TestClassScannerTest.SingleInvocationClassTemplateProvider.class)
@ContextConfiguration(classes = TestClassScannerTest.TestConfiguration.class)
public class TestClassScannerTest {
    @Test
    void includesNestedTestClassesForJupiterClassTemplates(@TempDir Path tempDir) throws Exception {
        try {
            Set<Class<?>> scannedClasses = new ScannerProcessor(testClasspathRoots(), tempDir)
                    .scanTestClasses()
                    .collect(Collectors.toSet());

            assertThat(scannedClasses)
                    .contains(TestClassScannerTest.class, NestedSpringTestCase.class,
                            NestedSpringTestCase.DeeplyNestedSpringTestCase.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Nested
    class NestedSpringTestCase {
        @Test
        void nestedTestCanBeDiscovered() {
            assertThat(true).isTrue();
        }

        @Nested
        class DeeplyNestedSpringTestCase {
            @Test
            void deeplyNestedTestCanBeDiscovered() {
                assertThat(true).isTrue();
            }
        }
    }

    public static class SingleInvocationClassTemplateProvider implements ClassTemplateInvocationContextProvider {
        @Override
        public boolean supportsClassTemplate(ExtensionContext context) {
            return context.getTestClass().filter(TestClassScannerTest.class::equals).isPresent();
        }

        @Override
        public Stream<? extends ClassTemplateInvocationContext> provideClassTemplateInvocationContexts(
                ExtensionContext context) {

            return Stream.of(new ClassTemplateInvocationContext() {
            });
        }
    }

    static class TestConfiguration {
    }

    private static Set<Path> testClasspathRoots() throws URISyntaxException {
        Path gradleTestClasses = Path.of("build", "classes", "java", "test").toAbsolutePath().normalize();
        if (Files.exists(gradleTestClasses)) {
            return Set.of(gradleTestClasses);
        }
        URL codeSource = TestClassScannerTest.class.getProtectionDomain().getCodeSource().getLocation();
        return Set.of(Path.of(codeSource.toURI()));
    }

    private static final class ScannerProcessor extends TestAotProcessor {
        private ScannerProcessor(Set<Path> classpathRoots, Path tempDir) {
            super(classpathRoots, Settings.builder()
                    .sourceOutput(tempDir.resolve("sources"))
                    .resourceOutput(tempDir.resolve("resources"))
                    .classOutput(tempDir.resolve("classes"))
                    .groupId("org.example")
                    .artifactId("spring-test-scanner")
                    .build());
        }

        private Stream<Class<?>> scanTestClasses() {
            return scanClasspathRoots();
        }

        @Override
        protected Void doProcess() {
            throw new UnsupportedOperationException("Only classpath scanning is needed");
        }
    }
}
