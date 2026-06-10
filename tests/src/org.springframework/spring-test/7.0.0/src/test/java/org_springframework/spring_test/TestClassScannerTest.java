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
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.context.aot.AbstractAotProcessor.Settings;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.aot.TestAotProcessor;

import static org.assertj.core.api.Assertions.assertThat;

@ClassTemplate
@ContextConfiguration(classes = TestClassScannerTest.TestConfiguration.class)
@ExtendWith(TestClassScannerTest.SingleInvocationClassTemplateProvider.class)
public class TestClassScannerTest {
    @Test
    void scansClassTemplatesAndExpandsNestedTestClasses(@TempDir Path outputDirectory) throws URISyntaxException {
        try {
            List<Class<?>> testClasses = new ExposedTestAotProcessor(Set.of(testClassPathRoot()), outputDirectory)
                    .scanTestClasses();

            assertThat(testClasses)
                    .contains(TestClassScannerTest.class, NestedSpringTest.class,
                            NestedSpringTest.DeeperNestedTest.class);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    private static Path testClassPathRoot() throws URISyntaxException {
        Path compiledTestClasses = Path.of("build/classes/java/test").toAbsolutePath();
        if (Files.isDirectory(compiledTestClasses)) {
            return compiledTestClasses;
        }
        CodeSource codeSource = TestClassScannerTest.class.getProtectionDomain().getCodeSource();
        if (codeSource != null) {
            return Path.of(codeSource.getLocation().toURI());
        }
        return compiledTestClasses;
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    @Nested
    class NestedSpringTest {
        @Nested
        class DeeperNestedTest {
        }
    }

    public static class TestConfiguration {
    }

    public static class SingleInvocationClassTemplateProvider implements ClassTemplateInvocationContextProvider {
        @Override
        public boolean supportsClassTemplate(ExtensionContext context) {
            return true;
        }

        @Override
        public Stream<? extends ClassTemplateInvocationContext> provideClassTemplateInvocationContexts(
                ExtensionContext context) {
            return Stream.of(new ClassTemplateInvocationContext() {
            });
        }
    }

    static class ExposedTestAotProcessor extends TestAotProcessor {
        ExposedTestAotProcessor(Set<Path> classpathRoots, Path outputDirectory) {
            super(classpathRoots, Settings.builder()
                    .sourceOutput(outputDirectory.resolve("sources"))
                    .resourceOutput(outputDirectory.resolve("resources"))
                    .classOutput(outputDirectory.resolve("classes"))
                    .groupId("org.example")
                    .artifactId("scanner-tests")
                    .build());
        }

        List<Class<?>> scanTestClasses() {
            return scanClasspathRoots().toList();
        }
    }
}
