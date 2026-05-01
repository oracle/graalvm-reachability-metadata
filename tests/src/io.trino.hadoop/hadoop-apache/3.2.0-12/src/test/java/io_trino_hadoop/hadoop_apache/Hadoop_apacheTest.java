/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_trino_hadoop.hadoop_apache;

import org.apache.hadoop.conf.Configurable;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.apache.hadoop.fs.permission.FsAction;
import org.apache.hadoop.fs.permission.FsPermission;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.DataInputBuffer;
import org.apache.hadoop.io.DataOutputBuffer;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.compress.BZip2Codec;
import org.apache.hadoop.io.compress.CompressionCodec;
import org.apache.hadoop.io.compress.CompressionInputStream;
import org.apache.hadoop.io.compress.CompressionOutputStream;
import org.apache.hadoop.io.compress.DefaultCodec;
import org.apache.hadoop.io.erasurecode.ErasureCoderOptions;
import org.apache.hadoop.io.erasurecode.rawcoder.XORRawDecoder;
import org.apache.hadoop.io.erasurecode.rawcoder.XORRawEncoder;
import org.apache.hadoop.io.file.tfile.TFile;
import org.apache.hadoop.util.LineReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class Hadoop_apacheTest {
    @TempDir
    File temporaryDirectory;

    @Test
    void pathAndPermissionApisNormalizeAndCombineValues() {
        Path base = new Path("file:///var/data/warehouse");
        Path child = new Path(base, "table/part-0001");
        Path suffixed = child.suffix(".copy");
        FsPermission permission = FsPermission.valueOf("-rwxr-x---");
        FsPermission umask = new FsPermission((short) 0027);

        assertThat(child.getName()).isEqualTo("part-0001");
        assertThat(child.getParent()).isEqualTo(new Path("file:///var/data/warehouse/table"));
        assertThat(child.depth()).isEqualTo(5);
        assertThat(suffixed.toString()).endsWith("/table/part-0001.copy");
        assertThat(Path.mergePaths(new Path("file:///var"), new Path("/log/app")))
                .isEqualTo(new Path("file:///var/log/app"));
        assertThat(permission.toShort()).isEqualTo((short) 0750);
        assertThat(permission.getUserAction()).isEqualTo(FsAction.ALL);
        assertThat(permission.getGroupAction().implies(FsAction.READ)).isTrue();
        assertThat(permission.applyUMask(umask).toShort()).isEqualTo((short) 0750);
        assertThat(FsAction.READ_WRITE.and(FsAction.READ_EXECUTE)).isEqualTo(FsAction.READ);
        assertThat(FsAction.READ.or(FsAction.EXECUTE)).isEqualTo(FsAction.READ_EXECUTE);
    }

    @Test
    void textAndWritableBuffersHandleUtf8AndVariableLengthData() throws Exception {
        Text text = new Text("hadoop π");
        text.append(" rocks".getBytes(StandardCharsets.UTF_8), 0, " rocks".length());
        DataOutputBuffer output = new DataOutputBuffer();

        text.write(output);
        Text.writeString(output, "tail value");
        DataInputBuffer input = new DataInputBuffer();
        input.reset(output.getData(), output.getLength());
        Text decoded = new Text();
        decoded.readFields(input);
        ByteBuffer encodedSnowman = Text.encode("snowman ☃");
        byte[] snowmanBytes = new byte[encodedSnowman.remaining()];
        encodedSnowman.get(snowmanBytes);

        assertThat(decoded.toString()).isEqualTo("hadoop π rocks");
        assertThat(decoded.find("π")).isGreaterThan(0);
        assertThat(decoded.charAt(decoded.find("π"))).isEqualTo('π');
        assertThat(decoded.copyBytes()).containsSequence("rocks".getBytes(StandardCharsets.UTF_8));
        assertThat(Text.readString(input)).isEqualTo("tail value");
        assertThat(Text.decode(snowmanBytes)).isEqualTo("snowman ☃");
    }

    @Test
    void lineReaderHonorsCustomDelimiterAndMaximumLineLength() throws Exception {
        byte[] delimiter = "||".getBytes(StandardCharsets.UTF_8);
        byte[] content = "alpha||βeta||gamma".getBytes(StandardCharsets.UTF_8);
        List<String> lines = new ArrayList<>();

        try (LineReader reader = new LineReader(new ByteArrayInputStream(content), 8, delimiter)) {
            Text line = new Text();
            while (reader.readLine(line) > 0) {
                lines.add(line.toString());
            }
        }

        Text truncated = new Text();
        ByteArrayInputStream truncatedInput = new ByteArrayInputStream("abc\ndef".getBytes(StandardCharsets.UTF_8));
        try (LineReader reader = new LineReader(truncatedInput)) {
            int consumed = reader.readLine(truncated, 3);
            assertThat(consumed).isEqualTo("abc\n".length());
        }

        assertThat(lines).containsExactly("alpha", "βeta", "gamma");
        assertThat(truncated.toString()).isEqualTo("abc");
    }

    @Test
    void rawLocalFileSystemSupportsReadWriteListingGlobAndRename() throws Exception {
        Configuration configuration = hadoopConfiguration();
        Path root = new Path(temporaryDirectory.toURI());
        Path directory = new Path(root, "fs-test");
        Path source = new Path(directory, "source.txt");
        Path target = new Path(directory, "renamed.txt");

        try (FileSystem fileSystem = rawLocalFileSystem(configuration)) {
            assertThat(fileSystem.mkdirs(directory, new FsPermission((short) 0755))).isTrue();
            try (FSDataOutputStream output = fileSystem.create(source, true)) {
                output.write("first line\nsecond line".getBytes(StandardCharsets.UTF_8));
                output.flush();
            }

            assertThat(fileSystem.exists(source)).isTrue();
            assertThat(fileSystem.getFileStatus(source).getLen()).isEqualTo("first line\nsecond line".length());
            try (FSDataInputStream input = fileSystem.open(source)) {
                byte[] bytes = new byte[(int) fileSystem.getFileStatus(source).getLen()];
                input.readFully(0, bytes);
                assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("first line\nsecond line");
            }

            FileStatus[] textFiles = fileSystem.globStatus(new Path(directory, "*.txt"));
            assertThat(Arrays.stream(textFiles).map(status -> status.getPath().getName()))
                    .containsExactly("source.txt");
            assertThat(fileSystem.rename(source, target)).isTrue();
            assertThat(fileSystem.exists(source)).isFalse();
            assertThat(fileSystem.exists(target)).isTrue();
            assertThat(fileSystem.delete(directory, true)).isTrue();
        }
    }

    @Test
    void compressionCodecsRoundTripPayloadsThroughPublicStreams() throws Exception {
        byte[] payload = String.join("\n", "alpha", "beta", "gamma", "delta", "epsilon")
                .getBytes(StandardCharsets.UTF_8);

        assertThat(roundTrip(new DefaultCodec(), payload)).isEqualTo(payload);
        assertThat(roundTrip(new BZip2Codec(), payload)).isEqualTo(payload);
    }

    @Test
    void sequenceFilePersistsMetadataAndWritableRecords() throws Exception {
        Configuration configuration = hadoopConfiguration();
        Path file = new Path(new File(temporaryDirectory, "records.seq").toURI());
        SequenceFile.Metadata metadata = new SequenceFile.Metadata();
        metadata.set(new Text("source"), new Text("hadoop-apache"));

        try (FileSystem fileSystem = rawLocalFileSystem(configuration);
                FSDataOutputStream output = fileSystem.create(file, true);
                SequenceFile.Writer writer = SequenceFile.createWriter(
                        configuration,
                        SequenceFile.Writer.stream(output),
                        SequenceFile.Writer.keyClass(IntWritable.class),
                        SequenceFile.Writer.valueClass(Text.class),
                        SequenceFile.Writer.metadata(metadata),
                        SequenceFile.Writer.compression(SequenceFile.CompressionType.NONE))) {
            writer.append(new IntWritable(1), new Text("one"));
            writer.append(new IntWritable(2), new Text("two"));
            writer.sync();
            writer.append(new IntWritable(3), new Text("three"));
        }

        List<String> records = new ArrayList<>();
        try (FileSystem fileSystem = rawLocalFileSystem(configuration);
                FSDataInputStream input = fileSystem.open(file);
                SequenceFile.Reader reader = new SequenceFile.Reader(
                        configuration,
                        SequenceFile.Reader.stream(input),
                        SequenceFile.Reader.length(fileSystem.getFileStatus(file).getLen()))) {
            IntWritable key = new IntWritable();
            Text value = new Text();
            assertThat(reader.getKeyClass()).isEqualTo(IntWritable.class);
            assertThat(reader.getValueClass()).isEqualTo(Text.class);
            assertThat(reader.getMetadata().get(new Text("source"))).isEqualTo(new Text("hadoop-apache"));
            while (reader.next(key, value)) {
                records.add(key.get() + ":" + value);
            }
        }

        assertThat(records).containsExactly("1:one", "2:two", "3:three");
    }

    @Test
    void tFileStoresSortedRecordsAndNamedMetaBlocks() throws Exception {
        Configuration configuration = hadoopConfiguration();
        Path file = new Path(new File(temporaryDirectory, "records.tfile").toURI());

        try (FileSystem fileSystem = rawLocalFileSystem(configuration);
                FSDataOutputStream output = fileSystem.create(file, true);
                TFile.Writer writer = new TFile.Writer(
                        output,
                        256,
                        TFile.COMPRESSION_NONE,
                        TFile.COMPARATOR_MEMCMP,
                        configuration)) {
            writer.append(bytes("key-001"), bytes("value-one"));
            writer.append(bytes("key-002"), bytes("value-two"));
            writer.append(bytes("key-003"), bytes("value-three"));
            try (DataOutputStream metaBlock = writer.prepareMetaBlock("summary")) {
                metaBlock.write(bytes("three records"));
            }
        }

        List<String> entries = new ArrayList<>();
        try (FileSystem fileSystem = rawLocalFileSystem(configuration);
                FSDataInputStream input = fileSystem.open(file);
                TFile.Reader reader = new TFile.Reader(input, fileSystem.getFileStatus(file).getLen(), configuration);
                TFile.Reader.Scanner scanner = reader.createScanner()) {
            assertThat(reader.getEntryCount()).isEqualTo(3);
            assertThat(reader.isSorted()).isTrue();
            assertThat(reader.getComparatorName()).isEqualTo(TFile.COMPARATOR_MEMCMP);
            byte[] firstKeyBuffer = reader.getFirstKey().buffer();
            int firstKeyOffset = reader.getFirstKey().offset();
            int firstKeySize = reader.getFirstKey().size();
            assertThat(toString(firstKeyBuffer, firstKeyOffset, firstKeySize)).isEqualTo("key-001");
            try (DataInputStream metaBlock = reader.getMetaBlock("summary")) {
                assertThat(new String(metaBlock.readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("three records");
            }
            while (!scanner.atEnd()) {
                BytesWritable key = new BytesWritable();
                BytesWritable value = new BytesWritable();
                scanner.entry().get(key, value);
                entries.add(toString(key) + "=" + toString(value));
                scanner.advance();
            }
        }

        assertThat(entries).containsExactly("key-001=value-one", "key-002=value-two", "key-003=value-three");
    }

    @Test
    void xorErasureCoderReconstructsMissingDataUnit() throws Exception {
        byte[][] data = new byte[][] {
                bytes("abcde"),
                bytes("ABCDE"),
                bytes("12345")
        };
        byte[][] parity = new byte[][] {new byte[5]};
        ErasureCoderOptions options = new ErasureCoderOptions(3, 1);

        XORRawEncoder encoder = new XORRawEncoder(options);
        encoder.encode(data, parity);
        encoder.release();

        byte[][] inputs = new byte[][] {data[0], null, data[2], parity[0]};
        byte[][] outputs = new byte[][] {new byte[5]};
        XORRawDecoder decoder = new XORRawDecoder(options);
        decoder.decode(inputs, new int[] {1}, outputs);
        decoder.release();

        assertThat(outputs[0]).isEqualTo(data[1]);
        assertThat(parity[0]).isEqualTo(xor(data));
    }

    private static Configuration hadoopConfiguration() {
        Configuration configuration = new Configuration(false);
        configuration.set("fs.defaultFS", "file:///");
        configuration.setClass("fs.file.impl", RawLocalFileSystem.class, FileSystem.class);
        configuration.set("io.serializations", "org.apache.hadoop.io.serializer.WritableSerialization");
        return configuration;
    }

    private static FileSystem rawLocalFileSystem(Configuration configuration) throws Exception {
        RawLocalFileSystem fileSystem = new RawLocalFileSystem();
        fileSystem.initialize(new Path("file:///").toUri(), configuration);
        return fileSystem;
    }

    private static byte[] roundTrip(CompressionCodec codec, byte[] payload) throws Exception {
        if (codec instanceof Configurable configurable) {
            configurable.setConf(hadoopConfiguration());
        }
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (CompressionOutputStream output = codec.createOutputStream(compressed)) {
            output.write(payload);
            output.finish();
        }
        ByteArrayInputStream compressedInput = new ByteArrayInputStream(compressed.toByteArray());
        try (CompressionInputStream input = codec.createInputStream(compressedInput)) {
            return input.readAllBytes();
        }
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String toString(BytesWritable writable) {
        return toString(writable.getBytes(), 0, writable.getLength());
    }

    private static String toString(byte[] bytes, int offset, int length) {
        return new String(bytes, offset, length, StandardCharsets.UTF_8);
    }

    private static byte[] xor(byte[][] values) {
        byte[] result = new byte[values[0].length];
        for (byte[] value : values) {
            for (int index = 0; index < value.length; index++) {
                result[index] = (byte) (result[index] ^ value[index]);
            }
        }
        return result;
    }
}
