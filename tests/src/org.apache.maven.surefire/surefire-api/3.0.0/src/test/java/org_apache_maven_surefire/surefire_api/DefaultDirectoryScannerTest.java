/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_surefire.surefire_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.apache.maven.surefire.api.util.DefaultDirectoryScanner;
import org.apache.maven.surefire.api.util.TestsToRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DefaultDirectoryScannerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    @SuppressWarnings("deprecation")
    public void locatesScannedTestClassesThroughTheSuppliedClassLoader() throws IOException {
        final Path classFile = temporaryDirectory.resolve(
                DefaultDirectoryScannerTest.class.getName().replace('.', '/') + ".class");
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, new byte[0]);

        final DefaultDirectoryScanner scanner = new DefaultDirectoryScanner(
                temporaryDirectory.toFile(),
                Collections.singletonList("**/*Test.class"),
                Collections.singletonList("**/*Ignored.class"),
                Collections.singletonList("**/DefaultDirectoryScannerTest.java"));

        final TestsToRun tests = scanner.locateTestClasses(
                DefaultDirectoryScannerTest.class.getClassLoader(),
                testClass -> testClass == DefaultDirectoryScannerTest.class);

        assertThat(tests.getLocatedClasses()).containsExactly(DefaultDirectoryScannerTest.class);
    }
}
