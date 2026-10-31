/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import java.io.File;
import java.net.URI;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.SequenceFile.CompressionType;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.DefaultCodec;
import org.apache.hadoop.io.serializer.WritableSerialization;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SequenceFileInnerReaderTest {
    @TempDir
    File temporaryDirectory;

    @ParameterizedTest
    @EnumSource(CompressionType.class)
    void readsTypedRecordsAndMetadata(CompressionType compression) throws Exception {
        Configuration configuration = new Configuration();
        configuration.setBoolean("fs.file.impl.disable.cache", true);
        configuration.setStrings("io.serializations", WritableSerialization.class.getName());
        Path path = new Path(new File(temporaryDirectory, "records.seq").toURI());
        SequenceFile.Metadata metadata = new SequenceFile.Metadata();
        metadata.set(new Text("dataset"), new Text("events"));
        DefaultCodec codec = new DefaultCodec();
        codec.setConf(configuration);

        try (FileSystem fileSystem = FileSystem.get(URI.create("file:///"), configuration)) {
            try (FSDataOutputStream output = fileSystem.create(path, false);
                    SequenceFile.Writer writer = SequenceFile.createWriter(configuration,
                            SequenceFile.Writer.stream(output),
                            SequenceFile.Writer.keyClass(IntWritable.class),
                            SequenceFile.Writer.valueClass(Text.class),
                            SequenceFile.Writer.metadata(metadata),
                            SequenceFile.Writer.compression(compression, codec))) {
                for (int index = 0; index < 4; index++) {
                    writer.append(new IntWritable(index), new Text("event-" + index));
                }
            }
            try (FSDataInputStream input = fileSystem.open(path);
                    SequenceFile.Reader reader = new SequenceFile.Reader(configuration,
                            SequenceFile.Reader.stream(input),
                            SequenceFile.Reader.length(fileSystem.getFileStatus(path).getLen()))) {
                assertEquals(IntWritable.class, reader.getKeyClass());
                assertEquals(Text.class, reader.getValueClass());
                assertEquals(compression != CompressionType.NONE, reader.isCompressed());
                assertEquals(compression == CompressionType.BLOCK, reader.isBlockCompressed());
                assertEquals(new Text("events"), reader.getMetadata().get(new Text("dataset")));
                IntWritable key = new IntWritable();
                Text value = new Text();
                for (int index = 0; index < 4; index++) {
                    assertTrue(reader.next(key, value));
                    assertEquals(index, key.get());
                    assertEquals("event-" + index, value.toString());
                }
                assertFalse(reader.next(key, value));
            }
        }
    }
}
