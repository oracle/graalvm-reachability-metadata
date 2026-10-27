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

import org.junit.jupiter.api.ClassTemplate;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ClassTemplateInvocationContext;
import org.junit.jupiter.api.extension.ClassTemplateInvocationContextProvider;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;

import org.springframework.context.aot.AbstractAotProcessor.Settings;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.aot.TestAotProcessor;

import static org.assertj.core.api.Assertions.assertThat;

@ClassTemplate
@ContextConfiguration
@ExtendWith(TestClassScannerTest.SingleInvocationProvider.class)
public class TestClassScannerTest {
    @Test
    void scansClassTemplateAndItsNestedSpringTestClasses() {
        Path testClasses = Path.of("build", "classes", "java", "test").toAbsolutePath();
        ScanningTestAotProcessor processor = new ScanningTestAotProcessor(Set.of(testClasses));

        Set<Class<?>> discovered;
        try (Stream<Class<?>> testClassesStream = processor.scanTestClasses()) {
            discovered = testClassesStream.collect(Collectors.toSet());
        }

        assertThat(discovered).contains(TestClassScannerTest.class, NestedSpringTestCase.class);
    }

    @Nested
    class NestedSpringTestCase {
    }

    static final class ScanningTestAotProcessor extends TestAotProcessor {
        ScanningTestAotProcessor(Set<Path> classpathRoots) {
            super(classpathRoots, Settings.builder()
                    .sourceOutput(Path.of("build", "generated", "aotSources"))
                    .resourceOutput(Path.of("build", "generated", "aotResources"))
                    .classOutput(Path.of("build", "generated", "aotClasses"))
                    .groupId("org.springframework")
                    .artifactId("spring-test")
                    .build());
        }

        Stream<Class<?>> scanTestClasses() {
            return scanClasspathRoots();
        }
    }

    static final class SingleInvocationProvider implements ClassTemplateInvocationContextProvider {
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

    static final class SingleInvocationContext implements ClassTemplateInvocationContext {
    }
}
