/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus_plexus_utils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.codehaus.plexus.util.DirectoryScanner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Scans an application source tree with Plexus directory scanning utilities.
 *
 * A build application discovers production sources while excluding generated
 * output and test fixtures from the compilation input.
 */
class SourceTreeScanningTest {

    @Test
    void findsProductionSourcesAndExcludesBuildInputs(@TempDir Path workspace) throws Exception {
        writeFile(workspace, "src/main/java/catalog/App.java", "class App {}\n");
        writeFile(workspace, "src/main/java/catalog/Version.java", "class Version {}\n");
        writeFile(workspace, "src/test/java/catalog/AppTest.java", "class AppTest {}\n");
        writeFile(workspace, "build/classes/catalog/App.class", "compiled\n");

        DirectoryScanner scanner = new DirectoryScanner();
        scanner.setBasedir(workspace.toFile());
        scanner.setIncludes(new String[] {"src/main/**/*.java"});
        scanner.setExcludes(new String[] {"**/generated/**"});
        scanner.scan();

        assertThat(Arrays.asList(scanner.getIncludedFiles()))
                .containsExactlyInAnyOrder(
                        "src/main/java/catalog/App.java", "src/main/java/catalog/Version.java");
        assertThat(Arrays.asList(scanner.getExcludedFiles())).isEmpty();
        assertThat(Arrays.asList(scanner.getNotIncludedFiles()))
                .contains("src/test/java/catalog/AppTest.java", "build/classes/catalog/App.class");
    }

    private void writeFile(Path workspace, String relativePath, String content) throws Exception {
        Path file = workspace.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
