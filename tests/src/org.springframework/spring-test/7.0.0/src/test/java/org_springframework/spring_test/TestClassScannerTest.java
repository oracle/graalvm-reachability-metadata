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
@ExtendWith(TestClassScannerTest.SingleInvocationProvider.class)
@ContextConfiguration(classes = TestClassScannerTest.TestConfiguration.class)
public class TestClassScannerTest extends BaseClassTemplateSpringTest {

    @Test
    void discoversNestedTestsDeclaredInJupiterClassTemplates() {
        Path testClassesRoot = Path.of("build", "classes", "java", "test").toAbsolutePath().normalize();
        assertThat(testClassesRoot).exists();

        try {
            List<Class<?>> testClasses = new ScanningTestAotProcessor(Set.of(testClassesRoot)).scanClasses();

            assertThat(testClasses)
                    .contains(TestClassScannerTest.class)
                    .contains(BaseClassTemplateSpringTest.InheritedNestedSpringTest.class)
                    .contains(TestClassScannerTest.NestedSpringTest.class)
                    .contains(TestClassScannerTest.NestedSpringTest.DeeperNestedSpringTest.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static final class ScanningTestAotProcessor extends TestAotProcessor {
        ScanningTestAotProcessor(Set<Path> classpathRoots) {
            super(classpathRoots, settings());
        }

        List<Class<?>> scanClasses() {
            try (Stream<Class<?>> testClasses = scanClasspathRoots()) {
                return testClasses.toList();
            }
        }

        private static Settings settings() {
            Path outputRoot = Path.of("build", "test-class-scanner-aot").toAbsolutePath().normalize();
            return Settings.builder()
                    .sourceOutput(outputRoot.resolve("sources"))
                    .resourceOutput(outputRoot.resolve("resources"))
                    .classOutput(outputRoot.resolve("classes"))
                    .groupId("org_springframework")
                    .artifactId("spring_test")
                    .build();
        }
    }

    @Nested
    class NestedSpringTest {
        @Test
        void nestedTest() {
        }

        @Nested
        class DeeperNestedSpringTest {
            @Test
            void deeperNestedTest() {
            }
        }
    }

    static class TestConfiguration {
    }

    public static class SingleInvocationProvider implements ClassTemplateInvocationContextProvider {
        @Override
        public boolean supportsClassTemplate(ExtensionContext context) {
            return true;
        }

        @Override
        public Stream<? extends ClassTemplateInvocationContext> provideClassTemplateInvocationContexts(
                ExtensionContext context) {

            return Stream.of(SingleInvocationContext.INSTANCE);
        }
    }

    enum SingleInvocationContext implements ClassTemplateInvocationContext {
        INSTANCE
    }
}

abstract class BaseClassTemplateSpringTest {
    @Nested
    class InheritedNestedSpringTest {
        @Test
        void inheritedNestedTest() {
        }
    }
}
