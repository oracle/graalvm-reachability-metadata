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
import org.springframework.test.context.aot.TestAotProcessor;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class TestClassScannerTest {
    @Test
    void scansNestedTestsDeclaredInJupiterClassTemplates() {
        try {
            Set<Class<?>> testClasses = new ScanningTestAotProcessor(Set.of(testClassesRoot()))
                    .scanForTestClasses();

            assertThat(testClasses).contains(
                    SpringClassTemplateTests.class,
                    AbstractSpringClassTemplateTests.InheritedNestedTests.class,
                    SpringClassTemplateTests.NestedSpringTests.class,
                    SpringClassTemplateTests.NestedSpringTests.RecursivelyNestedSpringTests.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static Path testClassesRoot() {
        return Path.of("build", "classes", "java", "test").toAbsolutePath().normalize();
    }

    static class ScanningTestAotProcessor extends TestAotProcessor {
        ScanningTestAotProcessor(Set<Path> classpathRoots) {
            super(classpathRoots, settings());
        }

        Set<Class<?>> scanForTestClasses() {
            try (Stream<Class<?>> testClasses = scanClasspathRoots()) {
                return testClasses.collect(Collectors.toSet());
            }
        }

        private static Settings settings() {
            Path outputRoot = Path.of("build", "test-class-scanner-aot");
            return Settings.builder()
                    .sourceOutput(outputRoot.resolve("sources"))
                    .resourceOutput(outputRoot.resolve("resources"))
                    .classOutput(outputRoot.resolve("classes"))
                    .groupId("org_springframework")
                    .artifactId("spring-test")
                    .build();
        }
    }

    abstract static class AbstractSpringClassTemplateTests {
        @Nested
        class InheritedNestedTests {
            @Test
            void inheritedNestedTest() {
                assertThat(true).isTrue();
            }
        }
    }

    @ClassTemplate
    @ExtendWith({SpringExtension.class, SingleInvocationProvider.class})
    public static class SpringClassTemplateTests extends AbstractSpringClassTemplateTests {
        @Test
        void classTemplateTest() {
            assertThat(true).isTrue();
        }

        @Nested
        class NestedSpringTests {
            @Test
            void nestedTest() {
                assertThat(true).isTrue();
            }

            @Nested
            class RecursivelyNestedSpringTests {
                @Test
                void recursivelyNestedTest() {
                    assertThat(true).isTrue();
                }
            }
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

            return Stream.of(new ClassTemplateInvocationContext() {
            });
        }
    }
}
