/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_compress;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CommonsCompressTest {

    private static final String TMP_DIR = System.getProperty("java.io.tmpdir");

    private File testDir;

    @BeforeAll
    void beforeAll() {
        testDir = new File(TMP_DIR, "metadata_compress_test_" + System.currentTimeMillis());
        testDir.mkdir();
    }

    @AfterAll
    void afterAll() {
        deleteDir(testDir);
    }

    @Test
    void testZipArchiveStreams() throws Exception {
        File zipFile = new File(testDir, "test.zip");
        try (ZipArchiveOutputStream zipOutputStream = new ZipArchiveOutputStream(zipFile)) {
            zipOutputStream.putArchiveEntry(new ZipArchiveEntry("test_file.txt"));
            zipOutputStream.write("Test Content".getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeArchiveEntry();
            zipOutputStream.finish();
        }

        File[] localFiles = testDir.listFiles();
        if (localFiles != null) {
            assertThat(localFiles)
                    .hasSize(1)
                    .extracting(File::getName)
                    .containsExactly("test.zip");
        } else {
            fail("'test.zip' not found in: " + testDir);
        }

        try (ZipArchiveInputStream zipInputStream = new ZipArchiveInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipArchiveEntry entry = zipInputStream.getNextZipEntry();
            try (OutputStream out = new FileOutputStream(new File(testDir, entry.getName()))) {
                IOUtils.copy(zipInputStream, out);
            }
        }

        localFiles = testDir.listFiles();
        if (localFiles != null) {
            assertThat(localFiles)
                    .hasSize(2)
                    .extracting(File::getName)
                    .contains("test.zip", "test_file.txt");
        } else {
            fail("'test.zip' and 'test_file.txt' not found in: " + testDir);
        }
    }

    private void deleteDir(File file) {
        if (file != null) {
            File[] childFiles = file.listFiles();
            if (childFiles != null) {
                for (File childFile : childFiles) {
                    deleteDir(childFile);
                }
            }
            boolean deleted = file.delete();
            if (!deleted) {
                System.out.println("File '" + file + " not deleted");
            }
        }
    }
}
