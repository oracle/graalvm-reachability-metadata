/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.Test;
import org.junit.jupiter.api.ClassTemplate;
import org.junit.jupiter.api.Nested;

import org.springframework.context.aot.AbstractAotProcessor.Settings;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.aot.TestAotProcessor;

import static org.assertj.core.api.Assertions.assertThat;

@ClassTemplate
@ContextConfiguration(classes = TestClassScannerTest.TestConfiguration.class)
public class TestClassScannerTest {
    @Test
    public void expandsJupiterClassTemplateNestedTestClasses() throws IOException, URISyntaxException {
        Path classpathRoot = compiledTestClassesRoot();
        Path outputDirectory = Files.createTempDirectory("spring-test-aot-scanner");
        ScanningTestAotProcessor processor = new ScanningTestAotProcessor(classpathRoot, outputDirectory);

        List<Class<?>> scannedClasses = processor.scan().toList();

        assertThat(scannedClasses).contains(TestClassScannerTest.class, NestedSpringScenario.class);
    }

    @Nested
    class NestedSpringScenario {
    }

    static class TestConfiguration {
    }

    private static Path compiledTestClassesRoot() throws URISyntaxException {
        String classFile = TestClassScannerTest.class.getName().replace('.', '/') + ".class";
        List<Path> candidates = new ArrayList<>();
        candidates.add(Path.of("build", "classes", "java", "test"));
        for (String classpathEntry : System.getProperty("java.class.path", "").split(File.pathSeparator)) {
            if (!classpathEntry.isBlank()) {
                candidates.add(Path.of(classpathEntry));
            }
        }
        for (Path candidate : candidates) {
            Path normalizedCandidate = candidate.toAbsolutePath().normalize();
            if (Files.exists(normalizedCandidate.resolve(classFile))) {
                return normalizedCandidate;
            }
        }

        URL classResource = TestClassScannerTest.class.getResource("TestClassScannerTest.class");
        assertThat(classResource).isNotNull();
        assertThat(classResource.getProtocol()).isEqualTo("file");
        Path resolvedClassFile = Path.of(classResource.toURI());
        return resolvedClassFile.getParent().getParent().getParent();
    }

    static class ScanningTestAotProcessor extends TestAotProcessor {
        ScanningTestAotProcessor(Path classpathRoot, Path outputDirectory) throws IOException {
            super(Set.of(classpathRoot), Settings.builder()
                    .sourceOutput(Files.createDirectories(outputDirectory.resolve("sources")))
                    .resourceOutput(Files.createDirectories(outputDirectory.resolve("resources")))
                    .classOutput(Files.createDirectories(outputDirectory.resolve("classes")))
                    .groupId("org.springframework")
                    .artifactId("spring-test")
                    .build());
        }

        Stream<Class<?>> scan() {
            return scanClasspathRoots();
        }
    }
}
