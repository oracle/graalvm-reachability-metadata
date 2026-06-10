/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.net.URI;
import java.nio.file.Path;
import java.security.CodeSource;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.aot.AbstractAotProcessor.Settings;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.aot.TestAotProcessor;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ParameterizedClass
@ValueSource(strings = "spring-test")
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = TestClassScannerTest.TestConfiguration.class)
public class TestClassScannerTest {
    private final String parameter;

    public TestClassScannerTest(String parameter) {
        this.parameter = parameter;
    }

    @Test
    void scansClassTemplateAndExpandsNestedTestClasses(
            @TempDir Path outputDirectory) throws Exception {
        assertThat(this.parameter).isEqualTo("spring-test");

        try {
            ExposedTestAotProcessor processor = new ExposedTestAotProcessor(
                    Set.of(testClassPathRoot()), settings(outputDirectory));
            List<Class<?>> scannedClasses = processor.scanSpringTestClasses()
                    .filter(testClass -> testClass.getName()
                            .startsWith(TestClassScannerTest.class.getName()))
                    .toList();

            assertThat(scannedClasses)
                    .contains(TestClassScannerTest.class, NestedSpringTestCase.class)
                    .doesNotContain(TestConfiguration.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    @Nested
    class NestedSpringTestCase {
        @Test
        void nestedTestClassIsDiscoverable() {
            assertThat(parameter).isEqualTo("spring-test");
        }
    }

    @Configuration
    static class TestConfiguration {
    }

    private static Settings settings(Path outputDirectory) {
        return Settings.builder()
                .sourceOutput(outputDirectory.resolve("sources"))
                .resourceOutput(outputDirectory.resolve("resources"))
                .classOutput(outputDirectory.resolve("classes"))
                .groupId("org.springframework")
                .artifactId("spring-test")
                .build();
    }

    private static Path testClassPathRoot() throws Exception {
        CodeSource codeSource = TestClassScannerTest.class.getProtectionDomain().getCodeSource();
        assertThat(codeSource).isNotNull();
        URI location = codeSource.getLocation().toURI();
        return Path.of(location);
    }

    private static class ExposedTestAotProcessor extends TestAotProcessor {
        ExposedTestAotProcessor(Set<Path> classpathRoots, Settings settings) {
            super(classpathRoots, settings);
        }

        Stream<Class<?>> scanSpringTestClasses() {
            return scanClasspathRoots();
        }
    }
}
