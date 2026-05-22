/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.rules.TemporaryFolder;

public class TemporaryFolderTest {
    @Test
    public void createsTemporaryFoldersWithAndWithoutExplicitParent() throws IOException {
        TemporaryFolder defaultFolder = new TemporaryFolder();
        File parentFolder = createParentFolder();
        TemporaryFolder folderWithParent = new TemporaryFolder(parentFolder);

        try {
            defaultFolder.create();
            assertCreatedTemporaryFolder(defaultFolder.getRoot());
            assertTrue(defaultFolder.newFile("sample.txt").isFile());

            folderWithParent.create();
            File rootWithParent = folderWithParent.getRoot();
            assertCreatedTemporaryFolder(rootWithParent);
            assertEquals(parentFolder.getCanonicalFile(), rootWithParent.getParentFile().getCanonicalFile());
            assertTrue(folderWithParent.newFolder("nested", "folder").isDirectory());
        } finally {
            defaultFolder.delete();
            folderWithParent.delete();
            deleteRecursively(parentFolder);
        }
    }

    private static File createParentFolder() throws IOException {
        File temporaryDirectory = new File(System.getProperty("java.io.tmpdir"));
        for (int index = 0; index < 100; index++) {
            File parentFolder = new File(
                    temporaryDirectory,
                    "junit-temporary-folder-" + System.nanoTime() + "-" + index);
            if (parentFolder.mkdir()) {
                return parentFolder;
            }
        }
        throw new IOException("Could not create parent folder in " + temporaryDirectory);
    }

    private static void assertCreatedTemporaryFolder(File folder) {
        assertNotNull(folder);
        assertTrue(folder.isDirectory());
        assertTrue(folder.getName().startsWith("junit"));
    }

    private static void deleteRecursively(File file) throws IOException {
        if (file == null || !file.exists()) {
            return;
        }

        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }

        if (!file.delete() && file.exists()) {
            throw new IOException("Could not delete " + file);
        }
    }
}
