/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_shell.spring_shell_core_autoconfigure;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.shell.core.autoconfigure.JLineShellAutoConfiguration.JLineHistoryConfiguration;

public final class PredefinedConfigurationClassProxyGenerator {
    private static final String DEBUG_LOCATION_PROPERTY = "cglib.debugLocation";
    private static final List<String> EXPECTED_CLASS_FILES = List.of(
            "org/springframework/shell/core/autoconfigure/JLineShellAutoConfiguration$JLineHistoryConfiguration$$SpringCGLIB$$0.class",
            "org/springframework/shell/core/autoconfigure/JLineShellAutoConfiguration$JLineHistoryConfiguration$$SpringCGLIB$$FastClass$$0.class",
            "org/springframework/shell/core/autoconfigure/JLineShellAutoConfiguration$JLineHistoryConfiguration$$SpringCGLIB$$FastClass$$1.class"
    );

    private PredefinedConfigurationClassProxyGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected a single output directory argument");
        }
        Path outputDirectory = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(outputDirectory);

        String previousDebugLocation = System.getProperty(DEBUG_LOCATION_PROPERTY);
        try {
            System.setProperty(DEBUG_LOCATION_PROPERTY, outputDirectory.toString());
            generateProxyClasses();
            verifyExpectedClassFiles(outputDirectory);
        } finally {
            restoreSystemProperty(DEBUG_LOCATION_PROPERTY, previousDebugLocation);
        }
    }

    private static void generateProxyClasses() {
        try (AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext(JLineHistoryConfiguration.class)) {
            context.getBean(JLineHistoryConfiguration.class);
        }
    }

    private static void verifyExpectedClassFiles(Path outputDirectory) throws IOException {
        List<String> missingClassFiles = EXPECTED_CLASS_FILES.stream()
                .filter(relativePath -> !Files.isRegularFile(outputDirectory.resolve(relativePath)))
                .toList();
        if (missingClassFiles.isEmpty()) {
            return;
        }

        List<String> availableClassFiles;
        try (Stream<Path> stream = Files.walk(outputDirectory)) {
            availableClassFiles = stream
                    .filter(Files::isRegularFile)
                    .map(outputDirectory::relativize)
                    .map(Path::toString)
                    .sorted()
                    .collect(Collectors.toList());
        }
        throw new IOException("Missing generated configuration proxy classes: " + missingClassFiles
                + "; available=" + availableClassFiles);
    }

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
            return;
        }
        System.setProperty(key, value);
    }
}
