/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import java.io.File;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;

import static org.assertj.core.api.Assertions.assertThat;

public class TemporaryFolderTest {
    @Test
    void createsTemporaryFoldersWithAndWithoutParentDirectory() throws Exception {
        TemporaryFolder defaultFolder = new TemporaryFolder();
        defaultFolder.create();
        try {
            assertThat(defaultFolder.getRoot()).isDirectory();
        } finally {
            defaultFolder.delete();
        }

        File parentDirectory = Files.createTempDirectory("junit-parent").toFile();
        TemporaryFolder childFolder = new TemporaryFolder(parentDirectory);
        try {
            childFolder.create();
            assertThat(childFolder.getRoot()).isDirectory();
            assertThat(childFolder.getRoot().getParentFile()).isEqualTo(parentDirectory);
        } finally {
            childFolder.delete();
            Files.delete(parentDirectory.toPath());
        }
    }
}
