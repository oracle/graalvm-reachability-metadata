/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.List;
import java.util.Set;
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

import org.springframework.context.annotation.Configuration;
import org.springframework.context.aot.AbstractAotProcessor.Settings;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.aot.TestAotProcessor;

import static org.assertj.core.api.Assertions.assertThat;

@ClassTemplate
@ContextConfiguration(classes = TestClassScannerTest.EmptyConfiguration.class)
@ExtendWith(TestClassScannerTest.SingleInvocationClassTemplateProvider.class)
public class TestClassScannerTest {
    @Test
    void scanExpandsJupiterClassTemplateToNestedSpringTestClasses(@TempDir Path outputDirectory) throws Exception {
        try {
            List<Class<?>> testClasses = scanTestClasses(outputDirectory);

            assertThat(testClasses)
                    .contains(TestClassScannerTest.class)
                    .contains(NestedSpringFixture.class)
                    .contains(NestedSpringFixture.RecursiveNestedSpringFixture.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static List<Class<?>> scanTestClasses(Path outputDirectory) throws Exception {
        Settings settings = Settings.builder()
                .sourceOutput(outputDirectory.resolve("sources"))
                .resourceOutput(outputDirectory.resolve("resources"))
                .classOutput(outputDirectory.resolve("classes"))
                .groupId("org.example")
                .artifactId("spring-test-scanner")
                .build();
        ExposingTestAotProcessor processor = new ExposingTestAotProcessor(Set.of(testClassesRoot()), settings);
        try (Stream<Class<?>> stream = processor.scan()) {
            return stream.toList();
        }
    }

    private static Path testClassesRoot() throws Exception {
        CodeSource codeSource = TestClassScannerTest.class.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            URI location = codeSource.getLocation().toURI();
            Path path = Path.of(location);
            if (Files.isDirectory(path)) {
                return path;
            }
        }
        return Path.of("build", "classes", "java", "test").toAbsolutePath().normalize();
    }

    public static class SingleInvocationClassTemplateProvider implements ClassTemplateInvocationContextProvider {
        @Override
        public boolean supportsClassTemplate(ExtensionContext context) {
            return true;
        }

        @Override
        public Stream<? extends ClassTemplateInvocationContext> provideClassTemplateInvocationContexts(
                ExtensionContext context) {

            return Stream.of(new SingleInvocationContext());
        }
    }

    private static final class SingleInvocationContext implements ClassTemplateInvocationContext {
    }

    @Configuration
    static class EmptyConfiguration {
    }

    @Nested
    class NestedSpringFixture {
        @Test
        void nestedTest() {
        }

        @Nested
        class RecursiveNestedSpringFixture {
            @Test
            void recursiveNestedTest() {
            }
        }
    }

    private static final class ExposingTestAotProcessor extends TestAotProcessor {
        private ExposingTestAotProcessor(Set<Path> classpathRoots, Settings settings) {
            super(classpathRoots, settings);
        }

        private Stream<Class<?>> scan() {
            return scanClasspathRoots();
        }

        @Override
        protected Void doProcess() {
            throw new UnsupportedOperationException("AOT processing is not needed");
        }
    }
}
