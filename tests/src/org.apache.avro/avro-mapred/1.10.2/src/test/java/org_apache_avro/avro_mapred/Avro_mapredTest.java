/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_avro.avro_mapred;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.file.CodecFactory;
import org.apache.avro.file.DataFileStream;
import org.apache.avro.file.DataFileWriter;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericFixed;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.hadoop.file.SortedKeyValueFile;
import org.apache.avro.hadoop.io.AvroDatumConverter;
import org.apache.avro.hadoop.io.AvroDatumConverterFactory;
import org.apache.avro.hadoop.io.AvroKeyValue;
import org.apache.avro.hadoop.io.AvroSequenceFile;
import org.apache.avro.hadoop.util.AvroCharSequenceComparator;
import org.apache.avro.io.DatumReader;
import org.apache.avro.mapred.AvroKey;
import org.apache.avro.mapred.AvroValue;
import org.apache.avro.mapred.AvroWrapper;
import org.apache.avro.mapred.Pair;
import org.apache.avro.mapreduce.AvroKeyValueRecordReader;
import org.apache.avro.mapreduce.AvroKeyValueRecordWriter;
import org.apache.avro.reflect.ReflectData;
import org.apache.avro.util.Utf8;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.ByteWritable;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.RawComparator;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobID;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.security.Credentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Avro_mapredTest {
    private static final Schema STRING_SCHEMA = Schema.create(Schema.Type.STRING);
    private static final Schema INT_SCHEMA = Schema.create(Schema.Type.INT);
    private static final Schema LONG_SCHEMA = Schema.create(Schema.Type.LONG);

    @TempDir
    private java.nio.file.Path tempDir;

    @Test
    void wrappersPairsAndKeyValueRecordsExposeAvroDataAndSchemas() {
        AvroWrapper<CharSequence> wrapper = new AvroWrapper<>("wrapped");
        AvroKey<CharSequence> key = new AvroKey<>("wrapped");
        AvroValue<Integer> value = new AvroValue<>(7);

        assertThat(wrapper.datum()).hasToString("wrapped");
        assertThat(wrapper).isEqualTo(new AvroWrapper<>("wrapped")).hasToString("wrapped");
        assertThat(key).isEqualTo(new AvroKey<>("wrapped")).hasSameHashCodeAs(new AvroKey<>("wrapped"));
        assertThat(value.datum()).isEqualTo(7);
        value.datum(8);
        assertThat(value.datum()).isEqualTo(8);

        Schema pairSchema = Pair.getPairSchema(STRING_SCHEMA, INT_SCHEMA);
        Pair<CharSequence, Integer> pair = new Pair<>("alpha", STRING_SCHEMA, 1, INT_SCHEMA);
        assertThat(pair.getSchema()).isEqualTo(pairSchema);
        assertThat(Pair.getKeySchema(pairSchema)).isEqualTo(STRING_SCHEMA);
        assertThat(Pair.getValueSchema(pairSchema)).isEqualTo(INT_SCHEMA);
        assertThat(pair.key()).hasToString("alpha");
        assertThat(pair.value()).isEqualTo(1);
        assertThat(pair.get(0)).hasToString("alpha");
        assertThat(pair.get(1)).isEqualTo(1);

        pair.put(0, new Utf8("beta"));
        pair.put(1, 2);
        assertThat(pair.key()).hasToString("beta");
        assertThat(pair.value()).isEqualTo(2);
        assertThat(pair).isEqualTo(new Pair<>(new Utf8("beta"), STRING_SCHEMA, 2, INT_SCHEMA));
        assertThat(pair.compareTo(new Pair<>("gamma", STRING_SCHEMA, 1, INT_SCHEMA))).isLessThan(0);

        AvroKeyValue<CharSequence, Integer> keyValue = new AvroKeyValue<>(new GenericData.Record(
                AvroKeyValue.getSchema(STRING_SCHEMA, INT_SCHEMA)));
        keyValue.setKey("first");
        keyValue.setValue(42);
        GenericRecord record = keyValue.get();
        assertThat(keyValue.getKey()).hasToString("first");
        assertThat(keyValue.getValue()).isEqualTo(42);
        assertThat(record.get(AvroKeyValue.KEY_FIELD)).hasToString("first");
        assertThat(record.get(AvroKeyValue.VALUE_FIELD)).isEqualTo(42);
    }

    @Test
    void avroJobHelpersConfigureLegacyMapReduceJobs() throws IOException {
        Schema outputSchema = Pair.getPairSchema(STRING_SCHEMA, LONG_SCHEMA);
        JobConf legacyJob = new JobConf(false);

        org.apache.avro.mapred.AvroJob.setInputSchema(legacyJob, STRING_SCHEMA);
        org.apache.avro.mapred.AvroJob.setMapOutputSchema(legacyJob, outputSchema);
        org.apache.avro.mapred.AvroJob.setOutputSchema(legacyJob, outputSchema);
        org.apache.avro.mapred.AvroJob.setOutputCodec(legacyJob, CodecFactory.deflateCodec(1).toString());
        org.apache.avro.mapred.AvroJob.setOutputMeta(legacyJob, "creator", "reachability-test");
        org.apache.avro.mapred.AvroJob.setOutputMeta(legacyJob, "records", 3L);
        org.apache.avro.mapred.AvroJob.setOutputMeta(legacyJob, "binary", new byte[] {1, 2, 3});
        org.apache.avro.mapred.AvroJob.setReflect(legacyJob);
        org.apache.avro.mapred.AvroJob.setDataModelClass(legacyJob, ReflectData.class);

        assertThat(org.apache.avro.mapred.AvroJob.getInputSchema(legacyJob)).isEqualTo(STRING_SCHEMA);
        assertThat(org.apache.avro.mapred.AvroJob.getMapOutputSchema(legacyJob)).isEqualTo(outputSchema);
        assertThat(org.apache.avro.mapred.AvroJob.getOutputSchema(legacyJob)).isEqualTo(outputSchema);
        assertThat(legacyJob.get(org.apache.avro.mapred.AvroJob.TEXT_PREFIX + "creator"))
                .isEqualTo("reachability-test");
        assertThat(legacyJob.get(org.apache.avro.mapred.AvroJob.TEXT_PREFIX + "records")).isEqualTo("3");
        assertThat(legacyJob.get(org.apache.avro.mapred.AvroJob.BINARY_PREFIX + "binary")).isNotBlank();
        assertThat(legacyJob.getBoolean(org.apache.avro.mapred.AvroJob.INPUT_IS_REFLECT, false)).isTrue();
        assertThat(legacyJob.getBoolean(org.apache.avro.mapred.AvroJob.MAP_OUTPUT_IS_REFLECT, false)).isTrue();
        assertThat(org.apache.avro.mapred.AvroJob.createInputDataModel(legacyJob)).isInstanceOf(ReflectData.class);
        assertThat(org.apache.avro.mapred.AvroJob.createMapOutputDataModel(legacyJob)).isInstanceOf(ReflectData.class);
        assertThat(org.apache.avro.mapred.AvroJob.createDataModel(legacyJob)).isInstanceOf(ReflectData.class);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void datumConverterFactoryConvertsAvroWrappersAndHadoopWritables() {
        JobConf conf = new JobConf(false);
        conf.setNumReduceTasks(0);
        org.apache.avro.hadoop.io.AvroSerialization.setKeyWriterSchema(conf, STRING_SCHEMA);
        org.apache.avro.hadoop.io.AvroSerialization.setValueWriterSchema(conf, INT_SCHEMA);
        AvroDatumConverterFactory factory = new AvroDatumConverterFactory(conf);

        AvroDatumConverter<AvroKey<CharSequence>, Object> keyConverter = factory.create((Class) AvroKey.class);
        AvroDatumConverter<AvroValue<Integer>, Integer> valueConverter = factory.create((Class) AvroValue.class);
        assertThat(keyConverter.getWriterSchema()).isEqualTo(STRING_SCHEMA);
        assertThat(keyConverter.convert(new AvroKey<>("map-key"))).hasToString("map-key");
        assertThat(valueConverter.getWriterSchema()).isEqualTo(INT_SCHEMA);
        assertThat(valueConverter.convert(new AvroValue<>(12))).isEqualTo(12);

        assertThat(factory.create(BooleanWritable.class).convert(new BooleanWritable(true))).isEqualTo(true);
        assertThat(factory.create(DoubleWritable.class).convert(new DoubleWritable(1.25D))).isEqualTo(1.25D);
        assertThat(factory.create(FloatWritable.class).convert(new FloatWritable(2.5F))).isEqualTo(2.5F);
        assertThat(factory.create(IntWritable.class).convert(new IntWritable(3))).isEqualTo(3);
        assertThat(factory.create(LongWritable.class).convert(new LongWritable(4L))).isEqualTo(4L);
        assertThat(factory.create(NullWritable.class).convert(NullWritable.get())).isNull();
        assertThat(factory.create(Text.class).convert(new Text("text"))).hasToString("text");
        AvroDatumConverter<BytesWritable, ByteBuffer> bytesConverter = factory.create(BytesWritable.class);
        ByteBuffer bytes = bytesConverter.convert(new BytesWritable(new byte[] {5, 6, 7}));
        assertThat(bytes.get(0)).isEqualTo((byte) 5);
        assertThat(bytes.get(1)).isEqualTo((byte) 6);
        assertThat(bytes.get(2)).isEqualTo((byte) 7);

        AvroDatumConverter<ByteWritable, GenericFixed> byteConverter = factory.create(ByteWritable.class);
        GenericFixed fixed = byteConverter.convert(new ByteWritable((byte) 9));
        assertThat(fixed.getSchema().getFixedSize()).isEqualTo(1);
        assertThat(fixed.bytes()).containsExactly((byte) 9);

        assertThatThrownBy(() -> factory.create(Object.class))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("java.lang.Object");
    }

    @Test
    void sortedKeyValueFileWritesIndexAndSupportsLookupAndIteration() throws IOException {
        Configuration conf = hadoopConfiguration();
        Path outputPath = new Path(tempDir.resolve("sorted-key-value").toUri());
        SortedKeyValueFile.Writer.Options writerOptions = new SortedKeyValueFile.Writer.Options()
                .withConfiguration(conf)
                .withPath(outputPath)
                .withKeySchema(STRING_SCHEMA)
                .withValueSchema(STRING_SCHEMA)
                .withIndexInterval(2)
                .withCodec(CodecFactory.nullCodec());

        try (SortedKeyValueFile.Writer<CharSequence, CharSequence> writer = new SortedKeyValueFile.Writer<>(
                writerOptions)) {
            writer.append("apple", "Apple");
            writer.append("banana", "Banana");
            writer.append("carrot", "Carrot");
            writer.append("durian", "Durian");
        }

        assertThat(tempDir.resolve("sorted-key-value").resolve(SortedKeyValueFile.DATA_FILENAME)).exists();
        assertThat(tempDir.resolve("sorted-key-value").resolve(SortedKeyValueFile.INDEX_FILENAME)).exists();

        SortedKeyValueFile.Reader.Options readerOptions = new SortedKeyValueFile.Reader.Options()
                .withConfiguration(conf)
                .withPath(outputPath)
                .withKeySchema(STRING_SCHEMA)
                .withValueSchema(STRING_SCHEMA);
        try (SortedKeyValueFile.Reader<CharSequence, CharSequence> reader = new SortedKeyValueFile.Reader<>(
                readerOptions)) {
            assertThat(reader.get("banana")).hasToString("Banana");
            assertThat(reader.get("blueberry")).isNull();
            assertThat(reader.get("aardvark")).isNull();
        }

        try (SortedKeyValueFile.Reader<CharSequence, CharSequence> reader = new SortedKeyValueFile.Reader<>(
                readerOptions)) {
            List<String> entries = new ArrayList<>();
            for (AvroKeyValue<CharSequence, CharSequence> entry : reader) {
                entries.add(entry.getKey() + "=" + entry.getValue());
            }
            assertThat(entries).containsExactly("apple=Apple", "banana=Banana", "carrot=Carrot", "durian=Durian");
        }

        SortedKeyValueFile.Writer.Options outOfOrderOptions = new SortedKeyValueFile.Writer.Options()
                .withConfiguration(conf)
                .withPath(new Path(tempDir.resolve("out-of-order").toUri()))
                .withKeySchema(STRING_SCHEMA)
                .withValueSchema(STRING_SCHEMA);
        try (SortedKeyValueFile.Writer<CharSequence, CharSequence> writer = new SortedKeyValueFile.Writer<>(
                outOfOrderOptions)) {
            writer.append("pear", "Pear");
            assertThatThrownBy(() -> writer.append("orange", "Orange"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sorted key order");
        }
    }

    @Test
    void avroSequenceFileRoundTripsAvroAndWritableRecords() throws IOException {
        Configuration conf = hadoopConfiguration();
        try (FileSystem fileSystem = FileSystem.get(conf)) {
            Path avroPath = new Path(tempDir.resolve("avro.seq").toUri());
            AvroSequenceFile.Writer.Options avroWriterOptions = new AvroSequenceFile.Writer.Options()
                    .withFileSystem(fileSystem)
                    .withConfiguration(conf)
                    .withOutputPath(avroPath)
                    .withKeySchema(STRING_SCHEMA)
                    .withValueSchema(INT_SCHEMA);

            try (SequenceFile.Writer writer = AvroSequenceFile.createWriter(avroWriterOptions)) {
                writer.append(new AvroKey<>("one"), new AvroValue<>(1));
                writer.append(new AvroKey<>("two"), new AvroValue<>(2));
            }

            AvroSequenceFile.Reader.Options avroReaderOptions = new AvroSequenceFile.Reader.Options()
                    .withFileSystem(fileSystem)
                    .withInputPath(avroPath)
                    .withConfiguration(conf)
                    .withKeySchema(STRING_SCHEMA)
                    .withValueSchema(INT_SCHEMA);
            try (SequenceFile.Reader reader = new AvroSequenceFile.Reader(avroReaderOptions)) {
                assertThat(reader.getMetadata().get(AvroSequenceFile.METADATA_FIELD_KEY_SCHEMA).toString())
                        .isEqualTo(STRING_SCHEMA.toString());
                assertThat(reader.getMetadata().get(AvroSequenceFile.METADATA_FIELD_VALUE_SCHEMA).toString())
                        .isEqualTo(INT_SCHEMA.toString());
                assertNextAvroEntry(reader, "one", 1);
                assertNextAvroEntry(reader, "two", 2);
                assertThat(reader.next(new AvroKey<CharSequence>())).isNull();
            }

            Path writablePath = new Path(tempDir.resolve("writable.seq").toUri());
            AvroSequenceFile.Writer.Options writableWriterOptions = new AvroSequenceFile.Writer.Options()
                    .withFileSystem(fileSystem)
                    .withConfiguration(conf)
                    .withOutputPath(writablePath)
                    .withKeyClass(Text.class)
                    .withValueClass(IntWritable.class);
            try (SequenceFile.Writer writer = AvroSequenceFile.createWriter(writableWriterOptions)) {
                writer.append(new Text("three"), new IntWritable(3));
                writer.append(new Text("four"), new IntWritable(4));
            }

            AvroSequenceFile.Reader.Options writableReaderOptions = new AvroSequenceFile.Reader.Options()
                    .withFileSystem(fileSystem)
                    .withInputPath(writablePath)
                    .withConfiguration(conf);
            try (SequenceFile.Reader reader = new AvroSequenceFile.Reader(writableReaderOptions)) {
                Text key = new Text();
                IntWritable value = new IntWritable();
                assertThat(reader.next(key)).isTrue();
                reader.getCurrentValue(value);
                assertThat(key).hasToString("three");
                assertThat(value.get()).isEqualTo(3);
                assertThat(reader.next(key)).isTrue();
                reader.getCurrentValue(value);
                assertThat(key).hasToString("four");
                assertThat(value.get()).isEqualTo(4);
                assertThat(reader.next(key)).isFalse();
            }
        }
    }

    @Test
    void avroKeyValueRecordWriterCreatesReadableAvroDataFiles() throws IOException {
        JobConf conf = new JobConf(false);
        AvroDatumConverterFactory factory = new AvroDatumConverterFactory(conf);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        AvroKeyValueRecordWriter<Text, IntWritable> writer = new AvroKeyValueRecordWriter<>(
                factory.create(Text.class),
                factory.create(IntWritable.class),
                GenericData.get(),
                CodecFactory.nullCodec(),
                output);

        Schema expectedSchema = AvroKeyValue.getSchema(STRING_SCHEMA, INT_SCHEMA);
        assertThat(writer.getWriterSchema()).isEqualTo(expectedSchema);
        writer.write(new Text("red"), new IntWritable(1));
        long syncPosition = writer.sync();
        writer.write(new Text("green"), new IntWritable(2));
        writer.close(null);

        assertThat(syncPosition).isPositive();
        DatumReader<GenericRecord> datumReader = new GenericDatumReader<>();
        try (DataFileStream<GenericRecord> reader = new DataFileStream<>(
                new ByteArrayInputStream(output.toByteArray()),
                datumReader)) {
            assertThat(reader.getSchema()).isEqualTo(expectedSchema);
            assertNextKeyValueEntry(reader, "red", 1);
            assertNextKeyValueEntry(reader, "green", 2);
            assertThat(reader.hasNext()).isFalse();
        }
    }

    @Test
    void mapreduceKeyValueRecordReaderReadsAvroKeyValueContainerFiles() throws IOException, InterruptedException {
        Schema keyValueSchema = AvroKeyValue.getSchema(STRING_SCHEMA, INT_SCHEMA);
        java.nio.file.Path avroFile = tempDir.resolve("mapreduce-key-value-input.avro");
        try (DataFileWriter<GenericRecord> writer = new DataFileWriter<>(new GenericDatumWriter<>(keyValueSchema))) {
            writer.create(keyValueSchema, avroFile.toFile());
            writer.append(keyValueRecord(keyValueSchema, "first", 10));
            writer.append(keyValueRecord(keyValueSchema, "second", 20));
        }

        Configuration conf = hadoopConfiguration();
        Path hadoopPath = new Path(avroFile.toUri());
        long fileLength = hadoopPath.getFileSystem(conf).getFileStatus(hadoopPath).getLen();
        InputSplit split = new FileSplit(hadoopPath, 0, fileLength, new String[0]);
        TaskAttemptContext context = new SimpleTaskAttemptContext(conf);
        RecordReader<AvroKey<CharSequence>, AvroValue<Integer>> reader = new AvroKeyValueRecordReader<>(
                STRING_SCHEMA,
                INT_SCHEMA);
        try {
            reader.initialize(split, context);
            assertNextMapreduceKeyValueEntry(reader, "first", 10);
            assertNextMapreduceKeyValueEntry(reader, "second", 20);
            assertThat(reader.nextKeyValue()).isFalse();
            assertThat(reader.getCurrentKey().datum()).isNull();
            assertThat(reader.getCurrentValue().datum()).isNull();
        } finally {
            reader.close();
        }
    }

    @Test
    void charSequenceComparatorOrdersDifferentCharSequenceImplementations() {
        AvroCharSequenceComparator<CharSequence> comparator = new AvroCharSequenceComparator<>();

        assertThat(comparator.compare("alpha", new Utf8("beta"))).isLessThan(0);
        assertThat(comparator.compare(new Utf8("beta"), "alpha")).isGreaterThan(0);
        assertThat(comparator.compare(new Utf8("same"), new StringBuilder("same"))).isZero();
        assertThat(AvroCharSequenceComparator.INSTANCE.compare("a", "b")).isLessThan(0);
    }

    private static Configuration hadoopConfiguration() {
        Configuration conf = new Configuration();
        conf.setBoolean("fs.file.impl.disable.cache", true);
        return conf;
    }

    @SuppressWarnings("unchecked")
    private static void assertNextAvroEntry(SequenceFile.Reader reader, String expectedKey, int expectedValue)
            throws IOException {
        AvroKey<CharSequence> key = (AvroKey<CharSequence>) reader.next(new AvroKey<CharSequence>());
        AvroValue<Integer> value = (AvroValue<Integer>) reader.getCurrentValue(new AvroValue<Integer>());
        assertThat(key).isNotNull();
        assertThat(key.datum()).hasToString(expectedKey);
        assertThat(value.datum()).isEqualTo(expectedValue);
    }

    private static void assertNextKeyValueEntry(DataFileStream<GenericRecord> reader, String expectedKey,
            int expectedValue) throws IOException {
        assertThat(reader.hasNext()).isTrue();
        AvroKeyValue<CharSequence, Integer> keyValue = new AvroKeyValue<>(reader.next());
        assertThat(keyValue.getKey()).hasToString(expectedKey);
        assertThat(keyValue.getValue()).isEqualTo(expectedValue);
    }

    private static GenericRecord keyValueRecord(Schema keyValueSchema, String key, int value) {
        GenericRecord record = new GenericData.Record(keyValueSchema);
        record.put(AvroKeyValue.KEY_FIELD, key);
        record.put(AvroKeyValue.VALUE_FIELD, value);
        return record;
    }

    private static void assertNextMapreduceKeyValueEntry(
            RecordReader<AvroKey<CharSequence>, AvroValue<Integer>> reader,
            String expectedKey,
            int expectedValue) throws IOException, InterruptedException {
        assertThat(reader.nextKeyValue()).isTrue();
        assertThat(reader.getCurrentKey().datum()).hasToString(expectedKey);
        assertThat(reader.getCurrentValue().datum()).isEqualTo(expectedValue);
    }

    private static final class SimpleTaskAttemptContext implements TaskAttemptContext {
        private final Configuration configuration;
        private String status = "";

        private SimpleTaskAttemptContext(Configuration configuration) {
            this.configuration = configuration;
        }

        @Override
        public Configuration getConfiguration() {
            return configuration;
        }

        @Override
        public Credentials getCredentials() {
            return new Credentials();
        }

        @Override
        public JobID getJobID() {
            return null;
        }

        @Override
        public int getNumReduceTasks() {
            return 0;
        }

        @Override
        public Path getWorkingDirectory() {
            return new Path(".");
        }

        @Override
        public Class<?> getOutputKeyClass() {
            return Object.class;
        }

        @Override
        public Class<?> getOutputValueClass() {
            return Object.class;
        }

        @Override
        public Class<?> getMapOutputKeyClass() {
            return Object.class;
        }

        @Override
        public Class<?> getMapOutputValueClass() {
            return Object.class;
        }

        @Override
        public String getJobName() {
            return "avro-mapred-test";
        }

        @Override
        public Class<? extends InputFormat<?, ?>> getInputFormatClass() {
            return null;
        }

        @Override
        public Class<? extends Mapper<?, ?, ?, ?>> getMapperClass() {
            return null;
        }

        @Override
        public Class<? extends Reducer<?, ?, ?, ?>> getCombinerClass() {
            return null;
        }

        @Override
        public Class<? extends Reducer<?, ?, ?, ?>> getReducerClass() {
            return null;
        }

        @Override
        public Class<? extends OutputFormat<?, ?>> getOutputFormatClass() {
            return null;
        }

        @Override
        public Class<? extends Partitioner<?, ?>> getPartitionerClass() {
            return null;
        }

        @Override
        public RawComparator<?> getSortComparator() {
            return null;
        }

        @Override
        public String getJar() {
            return null;
        }

        @Override
        public RawComparator<?> getCombinerKeyGroupingComparator() {
            return null;
        }

        @Override
        public RawComparator<?> getGroupingComparator() {
            return null;
        }

        @Override
        public boolean getJobSetupCleanupNeeded() {
            return false;
        }

        @Override
        public boolean getTaskCleanupNeeded() {
            return false;
        }

        @Override
        public boolean getProfileEnabled() {
            return false;
        }

        @Override
        public String getProfileParams() {
            return "";
        }

        @Override
        public Configuration.IntegerRanges getProfileTaskRange(boolean isMap) {
            return new Configuration.IntegerRanges();
        }

        @Override
        public String getUser() {
            return "test";
        }

        @Override
        public boolean getSymlink() {
            return false;
        }

        @Override
        public Path[] getArchiveClassPaths() {
            return new Path[0];
        }

        @Override
        public URI[] getCacheArchives() {
            return new URI[0];
        }

        @Override
        public URI[] getCacheFiles() {
            return new URI[0];
        }

        @Override
        public Path[] getLocalCacheArchives() {
            return new Path[0];
        }

        @Override
        public Path[] getLocalCacheFiles() {
            return new Path[0];
        }

        @Override
        public Path[] getFileClassPaths() {
            return new Path[0];
        }

        @Override
        public String[] getArchiveTimestamps() {
            return new String[0];
        }

        @Override
        public String[] getFileTimestamps() {
            return new String[0];
        }

        @Override
        public int getMaxMapAttempts() {
            return 1;
        }

        @Override
        public int getMaxReduceAttempts() {
            return 1;
        }

        @Override
        public TaskAttemptID getTaskAttemptID() {
            return null;
        }

        @Override
        public void setStatus(String status) {
            this.status = status;
        }

        @Override
        public String getStatus() {
            return status;
        }

        @Override
        public float getProgress() {
            return 0.0F;
        }

        @Override
        public Counter getCounter(Enum<?> counterName) {
            return null;
        }

        @Override
        public Counter getCounter(String groupName, String counterName) {
            return null;
        }

        @Override
        public void progress() {
            // No progress reporter is needed for this local reader test.
        }
    }
}
