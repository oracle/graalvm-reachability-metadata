/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import org.apache.maven.surefire.util.DefaultDirectoryScanner;
import org.apache.maven.surefire.util.ScannerFilter;
import org.apache.maven.surefire.util.TestsToRun;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultDirectoryScannerTest {
    @Test
    void locatesAndLoadsScannedTestClasses(@TempDir Path classesDirectory) throws IOException {
        Class<?> expectedClass = DefaultDirectoryScannerTest.class;
        createScannedClassFile(classesDirectory, expectedClass);

        List<String> includes = Collections.singletonList("**/DefaultDirectoryScannerTest.class");
        DefaultDirectoryScanner scanner = new DefaultDirectoryScanner(
                classesDirectory.toFile(),
                includes,
                Collections.emptyList(),
                null
        );
        ScannerFilter scannerFilter = testClass -> testClass == expectedClass;

        try {
            TestsToRun testsToRun = scanner.locateTestClasses(
                    DefaultDirectoryScannerTest.class.getClassLoader(),
                    scannerFilter
            );

            assertThat(testsToRun.getLocatedClasses()).containsExactly(expectedClass);
            assertThat(testsToRun.getClassByName(expectedClass.getName())).isEqualTo(expectedClass);
        } catch (Error e) {
            if (!NativeImageSupport.isUnsupportedFeatureError(e)) {
                throw e;
            }
        }
    }

    private static void createScannedClassFile(Path classesDirectory, Class<?> scannedClass) throws IOException {
        Path classFile = classesDirectory.resolve(scannedClass.getName().replace('.', File.separatorChar) + ".class");
        Files.createDirectories(classFile.getParent());
        Files.createFile(classFile);
    }
}
