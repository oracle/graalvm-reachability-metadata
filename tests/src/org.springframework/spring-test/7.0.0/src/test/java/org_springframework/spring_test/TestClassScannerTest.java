/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.nio.file.Path;
import java.util.LinkedHashSet;
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
@ContextConfiguration
@ExtendWith(TestClassScannerTest.SingleInvocationProvider.class)
public class TestClassScannerTest {
    @Test
    void scansNestedClassesDeclaredInJupiterClassTemplates() {
        try {
            ScannerProcessor processor = new ScannerProcessor(compiledTestClassesRoot());

            Set<Class<?>> testClasses = processor.scanTestClasses();

            assertThat(testClasses)
                    .contains(TestClassScannerTest.class, TestClassScannerTest.NestedSpringTestCase.class);
        } catch (Error error) {
            rethrowIfNotNativeImageDynamicClassLoadingError(error);
        }
    }

    private static Path compiledTestClassesRoot() {
        return Path.of("build", "classes", "java", "test").toAbsolutePath().normalize();
    }

    private static void rethrowIfNotNativeImageDynamicClassLoadingError(Error error) {
        if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
            throw error;
        }
    }

    @Nested
    class NestedSpringTestCase {
        @Test
        void nestedTest() {
        }
    }

    public static class SingleInvocationProvider implements ClassTemplateInvocationContextProvider {
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

    static class SingleInvocationContext implements ClassTemplateInvocationContext {
    }

    static class ScannerProcessor extends TestAotProcessor {
        ScannerProcessor(Path classpathRoot) {
            super(Set.of(classpathRoot), settings());
        }

        Set<Class<?>> scanTestClasses() {
            try (Stream<Class<?>> stream = scanClasspathRoots()) {
                return stream.collect(Collectors.toCollection(LinkedHashSet::new));
            }
        }

        private static Settings settings() {
            Path outputRoot = Path.of("build", "test-class-scanner-aot-output").toAbsolutePath().normalize();
            return Settings.builder()
                    .sourceOutput(outputRoot.resolve("sources"))
                    .resourceOutput(outputRoot.resolve("resources"))
                    .classOutput(outputRoot.resolve("classes"))
                    .groupId("org_springframework")
                    .artifactId("spring_test")
                    .build();
        }
    }
}
