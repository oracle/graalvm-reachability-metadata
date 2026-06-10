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
import org.junit.jupiter.api.io.TempDir;

import org.springframework.context.aot.AbstractAotProcessor.Settings;
import org.springframework.test.context.aot.TestAotProcessor;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

public class TestClassScannerTest {
    @Test
    void expandsSpringClassTemplateToNestedTestClasses(@TempDir Path outputDirectory) {
        Path classpathRoot = Path.of("build/classes/java/test").toAbsolutePath();
        assertThat(classpathRoot).isDirectory();
        ScanningTestAotProcessor processor = new ScanningTestAotProcessor(Set.of(classpathRoot), outputDirectory);

        ScanResult scanResult = scanForTestClasses(processor);

        if (scanResult.unsupportedDynamicClassLoading()) {
            assertThat(scanResult.scannedTestClasses()).isEmpty();
        }
        else {
            assertThat(scanResult.scannedTestClasses())
                    .contains(
                            TestClassTemplateSpringTestCase.class,
                            TestClassTemplateSpringTestCase.NestedSpringTestCase.class,
                            BaseSpringClassTemplateTestCase.InheritedNestedSpringTestCase.class)
                    .doesNotContain(
                            TestClassTemplateSpringTestCase.PlainInnerClass.class,
                            TestClassTemplateSpringTestCase.AbstractNestedSpringTestCase.class);
        }
    }

    private static ScanResult scanForTestClasses(ScanningTestAotProcessor processor) {
        try {
            Set<Class<?>> scannedTestClasses = processor.scanForTestClasses()
                    .filter(testClass -> testClass.getName().startsWith(TestClassScannerTest.class.getName()))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            return new ScanResult(scannedTestClasses, false);
        }
        catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return new ScanResult(Set.of(), true);
        }
    }

    record ScanResult(Set<Class<?>> scannedTestClasses, boolean unsupportedDynamicClassLoading) {
    }

    static class ScanningTestAotProcessor extends TestAotProcessor {
        ScanningTestAotProcessor(Set<Path> classpathRoots, Path outputDirectory) {
            super(classpathRoots, Settings.builder()
                    .sourceOutput(outputDirectory.resolve("sources"))
                    .resourceOutput(outputDirectory.resolve("resources"))
                    .classOutput(outputDirectory.resolve("classes"))
                    .groupId("org_springframework")
                    .artifactId("spring-test-scanner-test")
                    .build());
        }

        Stream<Class<?>> scanForTestClasses() {
            return scanClasspathRoots();
        }
    }

    static class BaseSpringClassTemplateTestCase {
        @Nested
        class InheritedNestedSpringTestCase {
            @Test
            void inheritedNestedTest() {
            }
        }
    }

    @ClassTemplate
    @ExtendWith({SpringExtension.class, SingleInvocationClassTemplateProvider.class})
    static class TestClassTemplateSpringTestCase extends BaseSpringClassTemplateTestCase {
        @Nested
        class NestedSpringTestCase {
            @Test
            void nestedTest() {
            }
        }

        @Nested
        abstract class AbstractNestedSpringTestCase {
        }

        class PlainInnerClass {
        }
    }

    static class SingleInvocationClassTemplateProvider implements ClassTemplateInvocationContextProvider {
        @Override
        public boolean supportsClassTemplate(ExtensionContext context) {
            return true;
        }

        @Override
        public Stream<ClassTemplateInvocationContext> provideClassTemplateInvocationContexts(ExtensionContext context) {
            return Stream.of(new SingleInvocationClassTemplateInvocationContext());
        }
    }

    static class SingleInvocationClassTemplateInvocationContext implements ClassTemplateInvocationContext {
        @Override
        public String getDisplayName(int invocationIndex) {
            return "single invocation";
        }
    }
}
