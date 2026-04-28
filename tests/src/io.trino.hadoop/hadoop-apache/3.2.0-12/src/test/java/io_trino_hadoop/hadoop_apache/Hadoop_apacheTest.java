/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.ContentSummary;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.io.ArrayWritable;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.MapWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparator;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionCodecFactory;
import org.apache.hadoop.io.compress.CompressionInputStream;
import org.apache.hadoop.io.compress.CompressionOutputStream;
import org.apache.hadoop.io.compress.DefaultCodec;
import org.apache.hadoop.io.compress.GzipCodec;
import org.apache.hadoop.util.LineReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class Hadoop_apacheTest {
    @TempDir
    java.nio.file.Path tempDir;

    @Test
    void configurationLoadsXmlResourcesAndTypedValues() throws Exception {
        String xml = """
                <configuration>
                  <property>
                    <name>trino.test.workers</name>
                    <value>7</value>
                  </property>
                  <property>
                    <name>trino.test.names</name>
                    <value>alpha, beta ,gamma</value>
                  </property>
                  <property>
                    <name>trino.test.endpoint</name>
                    <value>localhost:19090</value>
                  </property>
                </configuration>
                """;
        Configuration conf = new Configuration(false);
        conf.addResource(new ByteArrayInputStream(xml.getBytes(UTF_8)), "inline-test-configuration");
        conf.setBoolean("trino.test.enabled", true);
        conf.setTimeDuration("trino.test.timeout", 2, TimeUnit.MINUTES);
        conf.setPattern("trino.test.pattern", Pattern.compile("hadoop-[0-9]+"));

        InetSocketAddress address = conf.getSocketAddr("trino.test.endpoint", "localhost", 1);

        assertThat(conf.getInt("trino.test.workers", 0)).isEqualTo(7);
        assertThat(conf.getTrimmedStringCollection("trino.test.names"))
                .containsExactly("alpha", "beta", "gamma");
        assertThat(conf.getBoolean("trino.test.enabled", false)).isTrue();
        assertThat(conf.getTimeDuration("trino.test.timeout", 0, TimeUnit.SECONDS)).isEqualTo(120);
        assertThat(conf.getPattern("trino.test.pattern", Pattern.compile("missing")).matcher("hadoop-320").matches())
                .isTrue();
        assertThat(address.getHostString()).isEqualTo("localhost");
        assertThat(address.getPort()).isEqualTo(19090);
        assertThat(conf.getPropertySources("trino.test.workers"))
                .contains("inline-test-configuration");
    }

    @Test
    void localFileSystemSupportsDataStreamsStatusGlobsAndRecursiveListing() throws Exception {
        Configuration conf = new Configuration();
        conf.setBoolean("fs.file.impl.disable.cache", true);
        Path root = new Path(tempDir.toUri());
        byte[] payload = "line-one\nline-two\nline-three".getBytes(UTF_8);

        try (FileSystem fs = newRawLocalFileSystem(conf)) {
            Path source = new Path(root, "nested/input.bin");
            assertThat(fs.mkdirs(source.getParent())).isTrue();

            try (FSDataOutputStream output = fs.create(source, true)) {
                output.writeUTF("header");
                output.writeInt(payload.length);
                output.write(payload);
            }

            FileStatus sourceStatus = fs.getFileStatus(source);
            assertThat(sourceStatus.isFile()).isTrue();
            assertThat(sourceStatus.getLen()).isGreaterThan((long) payload.length);

            try (FSDataInputStream input = fs.open(source)) {
                assertThat(input.readUTF()).isEqualTo("header");
                assertThat(input.readInt()).isEqualTo(payload.length);
                byte[] roundTripped = new byte[payload.length];
                input.readFully(roundTripped);
                assertThat(roundTripped).isEqualTo(payload);

                input.seek(0);
                assertThat(input.getPos()).isZero();
                assertThat(input.readUTF()).isEqualTo("header");
            }

            Path renamed = new Path(root, "nested/renamed.data");
            assertThat(fs.rename(source, renamed)).isTrue();
            assertThat(fs.exists(source)).isFalse();
            assertThat(fs.isFile(renamed)).isTrue();

            FileStatus[] globbed = fs.globStatus(new Path(root, "nested/*.data"));
            assertThat(globbed).extracting(status -> status.getPath().getName())
                    .containsExactly("renamed.data");

            List<String> listedNames = new ArrayList<>();
            RemoteIterator<LocatedFileStatus> iterator = fs.listFiles(root, true);
            while (iterator.hasNext()) {
                listedNames.add(iterator.next().getPath().getName());
            }
            assertThat(listedNames).contains("renamed.data");

            ContentSummary summary = fs.getContentSummary(root);
            assertThat(summary.getLength()).isGreaterThanOrEqualTo(sourceStatus.getLen());
            assertThat(summary.getFileCount()).isGreaterThanOrEqualTo(1L);
        }
    }

    @Test
    void gzipCodecFactoryCompressesAndDecompressesStreams() throws Exception {
        Configuration conf = new Configuration(false);
        @SuppressWarnings("rawtypes")
        List<Class> codecClasses = List.of(GzipCodec.class);
        CompressionCodecFactory.setCodecClasses(conf, codecClasses);
        CompressionCodecFactory factory = new CompressionCodecFactory(conf);
        CompressionCodec codec = factory.getCodec(new Path("events.json.gz"));
        byte[] input = "{\"event\":\"created\",\"library\":\"hadoop-apache\"}\n".repeat(8).getBytes(UTF_8);

        ByteArrayOutputStream compressedBuffer = new ByteArrayOutputStream();
        try (CompressionOutputStream compressed = codec.createOutputStream(compressedBuffer)) {
            compressed.write(input);
            compressed.finish();
        }

        ByteArrayOutputStream decompressedBuffer = new ByteArrayOutputStream();
        try (CompressionInputStream decompressed = codec.createInputStream(
                new ByteArrayInputStream(compressedBuffer.toByteArray()))) {
            decompressed.transferTo(decompressedBuffer);
        }

        assertThat(codec).isInstanceOf(GzipCodec.class);
        assertThat(codec.getDefaultExtension()).isEqualTo(".gz");
        assertThat(factory.getCodecClassByName(GzipCodec.class.getName())).isEqualTo(GzipCodec.class);
        assertThat(CompressionCodecFactory.removeSuffix("events.json.gz", codec.getDefaultExtension()))
                .isEqualTo("events.json");
        assertThat(decompressedBuffer.toByteArray()).isEqualTo(input);
    }

    @Test
    void sequenceFilePersistsMetadataAndBlockCompressedWritableRecords() throws Exception {
        Configuration conf = new Configuration();
        conf.setBoolean("fs.file.impl.disable.cache", true);
        Path file = new Path(tempDir.resolve("records.seq").toUri());
        SequenceFile.Metadata metadata = new SequenceFile.Metadata();
        metadata.set(new Text("purpose"), new Text("native-image integration"));
        metadata.set(new Text("format"), new Text("text-to-int"));
        DefaultCodec defaultCodec = new DefaultCodec();
        defaultCodec.setConf(conf);

        try (FileSystem fs = newRawLocalFileSystem(conf);
                FSDataOutputStream output = fs.create(file, true);
                SequenceFile.Writer writer = SequenceFile.createWriter(
                        conf,
                        SequenceFile.Writer.stream(output),
                        SequenceFile.Writer.keyClass(Text.class),
                        SequenceFile.Writer.valueClass(IntWritable.class),
                        SequenceFile.Writer.metadata(metadata),
                        SequenceFile.Writer.compression(SequenceFile.CompressionType.BLOCK, defaultCodec))) {
            writer.append(new Text("alpha"), new IntWritable(11));
            writer.append(new Text("beta"), new IntWritable(22));
            writer.append(new Text("gamma"), new IntWritable(33));
            writer.sync();
        }

        Map<String, Integer> records = new LinkedHashMap<>();
        try (FileSystem fs = newRawLocalFileSystem(conf);
                FSDataInputStream input = fs.open(file);
                SequenceFile.Reader reader = new SequenceFile.Reader(conf, SequenceFile.Reader.stream(input))) {
            assertThat(reader.getKeyClass()).isEqualTo(Text.class);
            assertThat(reader.getValueClass()).isEqualTo(IntWritable.class);
            assertThat(reader.getCompressionType()).isEqualTo(SequenceFile.CompressionType.BLOCK);
            assertThat(reader.getMetadata().get(new Text("purpose")).toString())
                    .isEqualTo("native-image integration");

            Text key = new Text();
            IntWritable value = new IntWritable();
            while (reader.next(key, value)) {
                records.put(key.toString(), value.get());
            }
        }

        assertThat(records).containsExactly(
                Map.entry("alpha", 11),
                Map.entry("beta", 22),
                Map.entry("gamma", 33));
    }

    private static RawLocalFileSystem newRawLocalFileSystem(Configuration conf) throws Exception {
        RawLocalFileSystem fs = new RawLocalFileSystem();
        fs.initialize(URI.create("file:///"), conf);
        return fs;
    }

    @Test
    void textWritableCollectionsComparatorsAndLineReaderHandleUtf8Data() throws Exception {
        Text text = new Text("alpha");
        byte[] suffix = "-βeta".getBytes(UTF_8);
        text.append(suffix, 0, suffix.length);
        Text later = new Text("omega");
        BytesWritable bytes = new BytesWritable(text.copyBytes());
        MapWritable map = new MapWritable();
        map.put(new Text("word"), text);
        map.put(new Text("bytes"), bytes);
        map.put(new Text("count"), new IntWritable(text.getLength()));
        ArrayWritable array = new ArrayWritable(Text.class, new Writable[] {new Text("first"), new Text("second")});

        assertThat(text.toString()).isEqualTo("alpha-βeta");
        assertThat(Text.decode(bytes.copyBytes())).isEqualTo("alpha-βeta");
        assertThat(text.find("βeta")).isGreaterThan(0);
        assertThat(text.charAt(6)).isEqualTo('β');
        assertThat(map).hasSize(3);
        assertThat(((IntWritable) map.get(new Text("count"))).get()).isEqualTo(text.getLength());
        assertThat(array.toStrings()).containsExactly("first", "second");
        assertThat(WritableComparator.compareBytes(
                text.getBytes(), 0, text.getLength(), later.getBytes(), 0, later.getLength()))
                .isLessThan(0);

        byte[] lines = "alpha-βeta|second line|third line".getBytes(UTF_8);
        try (LineReader reader = new LineReader(new ByteArrayInputStream(lines), "|".getBytes(UTF_8))) {
            Text line = new Text();
            assertThat(reader.readLine(line)).isGreaterThan(0);
            assertThat(line.toString()).isEqualTo("alpha-βeta");
            assertThat(reader.readLine(line)).isGreaterThan(0);
            assertThat(line.toString()).isEqualTo("second line");
            assertThat(reader.readLine(line)).isGreaterThan(0);
            assertThat(line.toString()).isEqualTo("third line");
            assertThat(reader.readLine(line)).isZero();
        }
    }
}
