/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.io.File;
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
@ExtendWith(TestClassScannerTest.SingleInvocationProvider.class)
@ContextConfiguration(classes = TestClassScannerTest.TestConfiguration.class)
public class TestClassScannerTest {
    @Test
    void expandsClassTemplateNestedTestClasses(@TempDir Path temporaryDirectory) throws Exception {
        List<Class<?>> testClasses;
        try {
            testClasses = new ScanningTestAotProcessor(Set.of(classpathRoot()), temporaryDirectory).scan();
        } catch (RuntimeException exception) {
            if (!hasUnsupportedFeatureCause(exception)) {
                throw exception;
            }
            return;
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
            return;
        }

        assertThat(testClasses)
                .contains(TestClassScannerTest.class, NestedSpringTestCase.class);
    }

    @Nested
    class NestedSpringTestCase {
        @Test
        void nestedTest() {
            assertThat(true).isTrue();
        }
    }

    public static class TestConfiguration {
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

    static class ScanningTestAotProcessor extends TestAotProcessor {
        ScanningTestAotProcessor(Set<Path> classpathRoots, Path temporaryDirectory) {
            super(classpathRoots, Settings.builder()
                    .sourceOutput(temporaryDirectory.resolve("sources"))
                    .resourceOutput(temporaryDirectory.resolve("resources"))
                    .classOutput(temporaryDirectory.resolve("classes"))
                    .groupId("org.springframework")
                    .artifactId("spring-test")
                    .build());
        }

        List<Class<?>> scan() {
            try (Stream<Class<?>> stream = scanClasspathRoots()) {
                return stream.toList();
            }
        }
    }

    private static boolean hasUnsupportedFeatureCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Path classpathRoot() throws URISyntaxException {
        String testClassResource = "org_springframework/spring_test/TestClassScannerTest.class";
        for (String entry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            Path path = Path.of(entry);
            if (Files.exists(path.resolve(testClassResource))) {
                return path;
            }
        }
        CodeSource codeSource = TestClassScannerTest.class.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        return Path.of(codeSource.getLocation().toURI());
    }
}
