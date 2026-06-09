/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class SequenceFileTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void readerSyncSeeksToRecordBoundaryAfterMarker() throws Exception {
        Configuration conf = new Configuration(false);
        Path file = new Path(tempDir.resolve("sync-records.seq").toUri());
        long syncPosition;

        try (RawLocalFileSystem fileSystem = createRawLocalFileSystem(conf)) {
            try (FSDataOutputStream outputStream = fileSystem.create(file, true);
                    SequenceFile.Writer writer = SequenceFile.createWriter(
                            conf,
                            SequenceFile.Writer.stream(outputStream),
                            SequenceFile.Writer.keyClass(Text.class),
                            SequenceFile.Writer.valueClass(IntWritable.class),
                            SequenceFile.Writer.compression(SequenceFile.CompressionType.NONE))) {
                writer.append(new Text("before-sync"), new IntWritable(1));
                syncPosition = writer.getLength();
                writer.sync();
                writer.append(new Text("after-sync"), new IntWritable(2));
            }

            long fileLength = fileSystem.getFileStatus(file).getLen();
            try (FSDataInputStream inputStream = fileSystem.open(file);
                    SequenceFile.Reader reader = new SequenceFile.Reader(
                            conf,
                            SequenceFile.Reader.stream(inputStream),
                            SequenceFile.Reader.length(fileLength))) {
                Text key = new Text();
                IntWritable value = new IntWritable();

                reader.sync(syncPosition);

                assertThat(reader.next(key, value)).isTrue();
                assertThat(key.toString()).isEqualTo("after-sync");
                assertThat(value.get()).isEqualTo(2);
                assertThat(reader.next(key, value)).isFalse();
            }
        }
    }

    @Test
    void writerAndReaderRoundTripWritableKeysValuesAndMetadata() throws Exception {
        Configuration conf = new Configuration(false);
        Path file = new Path(tempDir.resolve("records.seq").toUri());
        SequenceFile.Metadata metadata = new SequenceFile.Metadata();
        metadata.set(new Text("source"), new Text("unit-test"));

        try (RawLocalFileSystem fileSystem = createRawLocalFileSystem(conf)) {
            try (FSDataOutputStream outputStream = fileSystem.create(file, true);
                    SequenceFile.Writer writer = SequenceFile.createWriter(
                            conf,
                            SequenceFile.Writer.stream(outputStream),
                            SequenceFile.Writer.keyClass(Text.class),
                            SequenceFile.Writer.valueClass(IntWritable.class),
                            SequenceFile.Writer.metadata(metadata),
                            SequenceFile.Writer.compression(SequenceFile.CompressionType.NONE))) {
                writer.append(new Text("one"), new IntWritable(1));
                writer.append(new Text("two"), new IntWritable(2));
            }

            long fileLength = fileSystem.getFileStatus(file).getLen();
            try (FSDataInputStream inputStream = fileSystem.open(file);
                    SequenceFile.Reader reader = new SequenceFile.Reader(
                            conf,
                            SequenceFile.Reader.stream(inputStream),
                            SequenceFile.Reader.length(fileLength))) {
                Text key = new Text();
                IntWritable value = new IntWritable();

                assertThat(reader.getKeyClass()).isEqualTo(Text.class);
                assertThat(reader.getValueClass()).isEqualTo(IntWritable.class);
                assertThat(reader.getMetadata().get(new Text("source")))
                        .isEqualTo(new Text("unit-test"));

                assertThat(reader.next(key, value)).isTrue();
                assertThat(key.toString()).isEqualTo("one");
                assertThat(value.get()).isEqualTo(1);

                assertThat(reader.next(key, value)).isTrue();
                assertThat(key.toString()).isEqualTo("two");
                assertThat(value.get()).isEqualTo(2);

                assertThat(reader.next(key, value)).isFalse();
            }
        }
    }

    private static RawLocalFileSystem createRawLocalFileSystem(Configuration conf)
            throws Exception {
        RawLocalFileSystem fileSystem = new RawLocalFileSystem();
        fileSystem.initialize(URI.create("file:///"), conf);
        return fileSystem;
    }
}
