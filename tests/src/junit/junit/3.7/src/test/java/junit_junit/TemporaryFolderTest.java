/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;

public class TemporaryFolderTest {

    @Test
    void createsRootFolderInDefaultTemporaryDirectory() throws IOException {
        TemporaryFolder temporaryFolder = new TemporaryFolder();

        temporaryFolder.create();
        try {
            File root = temporaryFolder.getRoot();

            assertThat(root).isDirectory();
            assertThat(root.getName()).startsWith("junit");
        } finally {
            temporaryFolder.delete();
        }
    }

    @Test
    void createsRootFolderInConfiguredParentDirectory() throws IOException {
        Path parent = Files.createTempDirectory("junit-parent");
        TemporaryFolder temporaryFolder = new TemporaryFolder(parent.toFile());

        try {
            temporaryFolder.create();
            File root = temporaryFolder.getRoot();

            assertThat(root).isDirectory();
            assertThat(root.getName()).startsWith("junit");
            File canonicalParent = parent.toFile().getCanonicalFile();
            assertThat(root.getParentFile().getCanonicalFile()).isEqualTo(canonicalParent);
        } finally {
            temporaryFolder.delete();
            Files.deleteIfExists(parent);
        }
    }
}
