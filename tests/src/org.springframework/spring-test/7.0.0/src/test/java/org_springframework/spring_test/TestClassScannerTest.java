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
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.ValueSource;

import org.graalvm.internal.tck.NativeImageSupport;

import org.springframework.context.aot.AbstractAotProcessor.Settings;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.aot.TestAotProcessor;

import static org.assertj.core.api.Assertions.assertThat;

public class TestClassScannerTest {
    @Test
    void scanClasspathRootsExpandsNestedTestsForJupiterClassTemplates() {
        try {
            Set<Path> classpathRoots = findTestClasspathRoots();
            ScanningTestAotProcessor processor = new ScanningTestAotProcessor(classpathRoots);

            Set<Class<?>> scannedClasses = processor.scanClasspathRootsForTests()
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            assertThat(scannedClasses)
                    .contains(ScannedClassTemplateTestCase.class,
                            ScannedClassTemplateTestCase.NestedSpringTestCase.class);
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    private static Set<Path> findTestClasspathRoots() {
        Set<Path> roots = new LinkedHashSet<>();
        Path gradleTestClasses = Path.of("build/classes/java/test").toAbsolutePath().normalize();
        if (Files.exists(gradleTestClasses)) {
            roots.add(gradleTestClasses);
        }
        URL classFile = TestClassScannerTest.class.getResource("TestClassScannerTest.class");
        if (classFile != null && "file".equals(classFile.getProtocol())) {
            try {
                roots.add(Path.of(classFile.toURI()).getParent().getParent().getParent());
            } catch (URISyntaxException ex) {
                throw new IllegalStateException("Cannot resolve test classpath root", ex);
            }
        }
        assertThat(roots).isNotEmpty();
        return roots;
    }

    static class ScanningTestAotProcessor extends TestAotProcessor {
        ScanningTestAotProcessor(Set<Path> classpathRoots) {
            super(classpathRoots, Settings.builder()
                    .sourceOutput(Path.of("build/generated/test-aot/sources"))
                    .resourceOutput(Path.of("build/generated/test-aot/resources"))
                    .classOutput(Path.of("build/generated/test-aot/classes"))
                    .groupId("org.springframework")
                    .artifactId("spring-test")
                    .build());
        }

        Stream<Class<?>> scanClasspathRootsForTests() {
            return scanClasspathRoots();
        }
    }

    @ParameterizedClass
    @ValueSource(strings = "sample")
    @ContextConfiguration(classes = ScannedConfiguration.class)
    static class ScannedClassTemplateTestCase {
        ScannedClassTemplateTestCase(String value) {
            assertThat(value).isEqualTo("sample");
        }

        @Test
        void classTemplateTest() {
        }

        @Nested
        class NestedSpringTestCase {
            @Test
            void nestedTest() {
            }
        }
    }

    static class ScannedConfiguration {
    }
}
