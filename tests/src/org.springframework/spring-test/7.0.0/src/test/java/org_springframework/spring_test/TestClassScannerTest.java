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
import java.nio.file.Paths;
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

@ContextConfiguration
@ClassTemplate
@ExtendWith(TestClassScannerTest.SingleInvocationClassTemplateProvider.class)
public class TestClassScannerTest {
    @Test
    void scanClasspathRootsExpandsJupiterClassTemplatesWithNestedClasses(@TempDir Path output) throws Exception {
        try {
            List<Class<?>> testClasses = new ScanningTestAotProcessor(Set.of(testClassPathRoot()), output)
                    .scanTestClasses();

            assertThat(testClasses).contains(
                    TestClassScannerTest.class,
                    ScannedNestedSpringTest.class,
                    ScannedNestedSpringTest.DeeplyNestedSpringTest.class);
            assertThat(testClasses).doesNotContain(PlainInnerFixture.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static Path testClassPathRoot() throws URISyntaxException {
        Path gradleTestClasses = Paths.get("build", "classes", "java", "test").toAbsolutePath();
        if (Files.exists(gradleTestClasses)) {
            return gradleTestClasses;
        }

        String resourceName = TestClassScannerTest.class.getName().replace('.', '/') + ".class";
        URL resource = TestClassScannerTest.class.getClassLoader().getResource(resourceName);
        assertThat(resource).isNotNull();
        Path classFile = Paths.get(resource.toURI());
        Path classPathRoot = classFile;
        for (String ignored : resourceName.split("/")) {
            classPathRoot = classPathRoot.getParent();
        }
        return classPathRoot;
    }

    private static final class ScanningTestAotProcessor extends TestAotProcessor {
        private ScanningTestAotProcessor(Set<Path> classpathRoots, Path output) {
            super(classpathRoots, Settings.builder()
                    .sourceOutput(output.resolve("sources"))
                    .resourceOutput(output.resolve("resources"))
                    .classOutput(output.resolve("classes"))
                    .groupId("org.example")
                    .artifactId("spring-test-scanner")
                    .build());
        }

        private List<Class<?>> scanTestClasses() {
            return scanClasspathRoots().toList();
        }
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

    @Nested
    class ScannedNestedSpringTest {
        @Nested
        class DeeplyNestedSpringTest {
        }
    }

    class PlainInnerFixture {
    }
}
