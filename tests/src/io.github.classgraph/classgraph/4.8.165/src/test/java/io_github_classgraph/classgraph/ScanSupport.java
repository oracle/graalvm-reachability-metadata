/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_github_classgraph.classgraph;

import io.github.classgraph.AnnotationInfo;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

final class ScanSupport {
    static final String PACKAGE = "io_github_classgraph.classgraph";
    static final String PAYLOAD = "catalog/payload.txt";
    static final String CONTENT = "ClassGraph resource payload\n";

    private ScanSupport() {
    }

    static Path archive(Path directory) throws IOException {
        Path archive = directory.resolve("catalog.jar");
        try (InputStream input = Objects.requireNonNull(
                ScanSupport.class.getResourceAsStream("/scan-fixtures.data"))) {
            Files.copy(input, archive);
        }
        return archive;
    }

    static ClassGraph graph(Path archive) {
        return new ClassGraph().overrideClasspath(archive.toFile())
                .overrideClassLoaders(ScanFixtures.class.getClassLoader())
                .enableAllInfo().acceptPackages(PACKAGE).acceptPaths("catalog");
    }

    static ClassInfo subject(ScanResult scan) {
        return Objects.requireNonNull(scan.getClassInfo(ScanFixtures.Subject.class.getName()));
    }

    static AnnotationInfo details(ScanResult scan) {
        return Objects.requireNonNull(subject(scan).getAnnotationInfo(ScanFixtures.Details.class.getName()));
    }

    static ScanResult discover(Path archive) {
        String previous = System.getProperty("java.class.path");
        try {
            System.setProperty("java.class.path", archive.toString());
            return new ClassGraph().disableModuleScanning()
                    .filterClasspathElements(path -> path.equals(archive.toString()))
                    .enableAllInfo().acceptPackages(PACKAGE).acceptPaths("catalog").scan(2);
        } finally {
            if (previous == null) {
                System.clearProperty("java.class.path");
            } else {
                System.setProperty("java.class.path", previous);
            }
        }
    }
}
