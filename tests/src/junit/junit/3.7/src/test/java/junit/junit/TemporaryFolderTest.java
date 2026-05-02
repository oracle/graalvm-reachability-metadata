/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TemporaryFolderTest {
    @TempDir
    Path parentDirectory;

    @Test
    public void createsTemporaryFolderInDefaultLocation() throws IOException {
        TemporaryFolder temporaryFolder = new TemporaryFolder();
        File rootDirectory = null;

        try {
            temporaryFolder.create();
            rootDirectory = temporaryFolder.getRoot();

            assertTrue(rootDirectory.isDirectory());
            assertTrue(rootDirectory.getName().startsWith("junit"));
        } finally {
            temporaryFolder.delete();
        }

        assertFalse(rootDirectory.exists());
    }

    @Test
    public void createsTemporaryFolderInsideExplicitParentDirectory() throws IOException {
        TemporaryFolder temporaryFolder = TemporaryFolder.builder()
                .parentFolder(parentDirectory.toFile())
                .build();
        File rootDirectory = null;

        try {
            temporaryFolder.create();
            rootDirectory = temporaryFolder.getRoot();

            assertTrue(rootDirectory.isDirectory());
            assertEquals(
                    parentDirectory.toFile().getCanonicalFile(),
                    rootDirectory.getParentFile().getCanonicalFile());

            File childDirectory = temporaryFolder.newFolder("child");
            File childFile = temporaryFolder.newFile("sample.txt");

            assertTrue(childDirectory.isDirectory());
            assertTrue(childFile.isFile());
            assertEquals(rootDirectory.getCanonicalFile(), childDirectory.getParentFile().getCanonicalFile());
            assertEquals(rootDirectory.getCanonicalFile(), childFile.getParentFile().getCanonicalFile());
        } finally {
            temporaryFolder.delete();
        }

        assertFalse(rootDirectory.exists());
    }
}
