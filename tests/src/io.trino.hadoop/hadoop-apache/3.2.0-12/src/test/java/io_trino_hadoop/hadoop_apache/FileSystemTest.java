/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import java.io.File;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FileSystemTest {
    @TempDir
    File temporaryDirectory;

    @Test
    void discoversLocalFileSystemAndManagesFiles() throws Exception {
        Configuration configuration = new Configuration();
        configuration.setBoolean("fs.file.impl.disable.cache", true);
        Path directory = new Path(new File(temporaryDirectory, "records").toURI());
        Path source = new Path(directory, "original.txt");
        Path destination = new Path(directory, "renamed.txt");
        byte[] payload = "first record\nsecond record\n".getBytes(StandardCharsets.UTF_8);

        try (FileSystem fileSystem = FileSystem.get(URI.create("file:///"), configuration)) {
            assertEquals("file", fileSystem.getScheme());
            assertTrue(fileSystem.mkdirs(directory));
            try (FSDataOutputStream output = fileSystem.create(source, false)) {
                output.write(payload);
            }
            assertEquals(payload.length, fileSystem.getFileStatus(source).getLen());
            try (FSDataInputStream input = fileSystem.open(source)) {
                byte[] record = new byte[6];
                input.readFully(6, record);
                assertArrayEquals("record".getBytes(StandardCharsets.UTF_8), record);
                input.seek(0);
                assertArrayEquals(payload, input.readAllBytes());
            }
            assertTrue(fileSystem.rename(source, destination));
            assertFalse(fileSystem.exists(source));
            assertEquals(1, fileSystem.listStatus(directory).length);
            assertEquals(destination.getName(), fileSystem.listStatus(directory)[0].getPath().getName());
            assertTrue(fileSystem.delete(directory, true));
            assertFalse(fileSystem.exists(directory));
        }
    }
}
