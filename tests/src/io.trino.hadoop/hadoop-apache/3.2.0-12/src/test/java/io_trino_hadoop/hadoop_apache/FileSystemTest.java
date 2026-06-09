/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FileSystemTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void localFileSystemWritesReadsListsAndDeletesFiles() throws Exception {
        Configuration conf = new Configuration(false);
        try (RawLocalFileSystem fileSystem = createRawLocalFileSystem(conf)) {
            Path directory = new Path(tempDir.resolve("warehouse").toUri());
            Path file = new Path(directory, "part-00000.txt");
            byte[] payload = "alpha\nbeta\n".getBytes(StandardCharsets.UTF_8);

            assertThat(fileSystem.mkdirs(directory)).isTrue();
            try (FSDataOutputStream outputStream = fileSystem.create(file, true)) {
                outputStream.write(payload);
            }

            byte[] actual = new byte[payload.length];
            try (FSDataInputStream inputStream = fileSystem.open(file)) {
                inputStream.readFully(actual);
            }

            FileStatus status = fileSystem.getFileStatus(file);
            assertThat(actual).isEqualTo(payload);
            assertThat(status.isFile()).isTrue();
            assertThat(status.getLen()).isEqualTo(payload.length);
            assertThat(fileSystem.listStatus(directory))
                    .extracting(FileStatus::getPath)
                    .contains(file);
            assertThat(fileSystem.delete(directory, true)).isTrue();
            assertThat(fileSystem.exists(directory)).isFalse();
        }
    }

    @Test
    void globStatusUsesPathFilters() throws Exception {
        Configuration conf = new Configuration(false);
        try (RawLocalFileSystem fileSystem = createRawLocalFileSystem(conf)) {
            Path directory = new Path(tempDir.resolve("glob").toUri());
            Path included = new Path(directory, "include.avro");
            Path skipped = new Path(directory, "skip.txt");
            fileSystem.mkdirs(directory);
            fileSystem.createNewFile(included);
            fileSystem.createNewFile(skipped);

            FileStatus[] statuses = fileSystem.globStatus(
                    new Path(directory, "*"), path -> path.getName().endsWith(".avro"));

            assertThat(statuses).hasSize(1);
            assertThat(statuses[0].getPath()).isEqualTo(included);
        }
    }

    private static RawLocalFileSystem createRawLocalFileSystem(Configuration conf)
            throws Exception {
        RawLocalFileSystem fileSystem = new RawLocalFileSystem();
        fileSystem.initialize(URI.create("file:///"), conf);
        return fileSystem;
    }
}
