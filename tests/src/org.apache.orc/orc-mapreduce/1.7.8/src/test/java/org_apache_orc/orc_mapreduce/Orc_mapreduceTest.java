/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_orc.orc_mapreduce;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configuration.IntegerRanges;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hive.ql.exec.vector.VectorizedRowBatch;
import org.apache.hadoop.hive.ql.io.sarg.PredicateLeaf;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgument;
import org.apache.hadoop.hive.ql.io.sarg.SearchArgumentFactory;
import org.apache.hadoop.io.BooleanWritable;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.FloatWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.RawComparator;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.JobID;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.OutputFormat;
import org.apache.hadoop.mapreduce.Partitioner;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.TaskAttemptID;
import org.apache.hadoop.mapreduce.TaskType;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.security.Credentials;
import org.apache.orc.ColumnStatistics;
import org.apache.orc.OrcConf;
import org.apache.orc.StripeInformation;
import org.apache.orc.StripeStatistics;
import org.apache.orc.TypeDescription;
import org.apache.orc.Writer;
import org.apache.orc.mapred.OrcKey;
import org.apache.orc.mapred.OrcList;
import org.apache.orc.mapred.OrcMap;
import org.apache.orc.mapred.OrcMapredRecordReader;
import org.apache.orc.mapred.OrcMapredRecordWriter;
import org.apache.orc.mapred.OrcStruct;
import org.apache.orc.mapred.OrcTimestamp;
import org.apache.orc.mapred.OrcUnion;
import org.apache.orc.mapred.OrcValue;
import org.apache.orc.mapreduce.OrcMapreduceRecordReader;
import org.apache.orc.mapreduce.OrcMapreduceRecordWriter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class Orc_mapreduceTest {
    @Test
    void mapreduceRecordWriterAndReaderRoundTripNestedWritableRows() throws Exception {
        TypeDescription schema = complexSchema();
        CapturingWriter writer = new CapturingWriter(schema);
        OrcMapreduceRecordWriter<Writable> recordWriter = new OrcMapreduceRecordWriter<>(writer, 1, 2);
        recordWriter.write(NullWritable.get(), createRow(schema, 1, "first", "left", true));
        recordWriter.write(NullWritable.get(), new OrcKey(createRow(schema, 2, "second", "right", false)));
        recordWriter.close(null);

        assertThat(writer.closed).isTrue();
        assertThat(writer.rows).hasSize(2);
        assertRow(writer.rows.get(0), 1, "first", "left", true);
        assertRow(writer.rows.get(1), 2, "second", "right", false);

        OrcMapreduceRecordReader<OrcStruct> recordReader = new OrcMapreduceRecordReader<>(
                new InMemoryRecordReader(schema, writer.rows), schema);
        try {
            assertThat(recordReader.getProgress()).isBetween(0.0f, 1.0f);

            assertThat(recordReader.nextKeyValue()).isTrue();
            assertThat(recordReader.getCurrentKey()).isSameAs(NullWritable.get());
            assertRow(recordReader.getCurrentValue(), 1, "first", "left", true);

            assertThat(recordReader.nextKeyValue()).isTrue();
            assertRow(recordReader.getCurrentValue(), 2, "second", "right", false);

            assertThat(recordReader.nextKeyValue()).isFalse();
            assertThat(recordReader.getProgress()).isEqualTo(1.0f);
        } finally {
            recordReader.close();
        }
    }

    @Test
    void mapredWritableObjectsExposeTypedValuesConfigurationAndOrdering() {
        TypeDescription shuffleSchema = TypeDescription.fromString("struct<id:int,name:string>");
        JobConf conf = new JobConf(new Configuration(false));
        OrcConf.MAPRED_SHUFFLE_KEY_SCHEMA.setString(conf, shuffleSchema.toString());
        OrcConf.MAPRED_SHUFFLE_VALUE_SCHEMA.setString(conf, shuffleSchema.toString());

        OrcKey configuredKey = new OrcKey();
        configuredKey.configure(conf);
        OrcStruct keyStruct = (OrcStruct) configuredKey.key;
        keyStruct.setAllFields(new IntWritable(7), new Text("key"));

        OrcStruct sameKeyStruct = new OrcStruct(shuffleSchema);
        sameKeyStruct.setFieldValue("id", new IntWritable(7));
        sameKeyStruct.setFieldValue("name", new Text("key"));
        OrcKey sameKey = new OrcKey(sameKeyStruct);
        assertThat(configuredKey).isEqualTo(sameKey);
        assertThat(configuredKey.compareTo(sameKey)).isZero();
        assertThat(configuredKey.hashCode()).isEqualTo(sameKey.hashCode());

        OrcValue configuredValue = new OrcValue();
        configuredValue.configure(conf);
        assertThat(configuredValue.value).isInstanceOf(OrcStruct.class);
        ((OrcStruct) configuredValue.value).setAllFields(new IntWritable(8), new Text("value"));
        assertThat(((OrcStruct) configuredValue.value).getFieldValue("name")).isEqualTo(new Text("value"));

        assertThat(OrcStruct.createValue(TypeDescription.createBoolean())).isInstanceOf(BooleanWritable.class);
        assertThat(OrcStruct.createValue(TypeDescription.createInt())).isInstanceOf(IntWritable.class);
        assertThat(OrcStruct.createValue(TypeDescription.createLong())).isInstanceOf(LongWritable.class);
        assertThat(OrcStruct.createValue(TypeDescription.createFloat())).isInstanceOf(FloatWritable.class);
        assertThat(OrcStruct.createValue(TypeDescription.createDouble())).isInstanceOf(DoubleWritable.class);
        assertThat(OrcStruct.createValue(TypeDescription.createString())).isInstanceOf(Text.class);
        assertThat(OrcStruct.createValue(TypeDescription.createBinary())).isInstanceOf(BytesWritable.class);

        OrcList<IntWritable> leftList = intList(TypeDescription.createList(TypeDescription.createInt()), 1, 3);
        OrcList<IntWritable> rightList = intList(TypeDescription.createList(TypeDescription.createInt()), 1, 3);
        assertThat((Object) leftList).isEqualTo(rightList);
        rightList.set(1, new IntWritable(4));
        assertThat(leftList.compareTo(rightList)).isNegative();

        TypeDescription mapSchema = TypeDescription.createMap(
                TypeDescription.createString(), TypeDescription.createInt());
        OrcMap<Text, IntWritable> leftMap = new OrcMap<>(mapSchema);
        leftMap.put(new Text("a"), new IntWritable(1));
        OrcMap<Text, IntWritable> rightMap = new OrcMap<>(mapSchema);
        rightMap.put(new Text("a"), new IntWritable(1));
        assertThat((Object) leftMap).isEqualTo(rightMap);

        TypeDescription unionSchema = TypeDescription.createUnion()
                .addUnionChild(TypeDescription.createInt())
                .addUnionChild(TypeDescription.createString());
        OrcUnion union = new OrcUnion(unionSchema);
        union.set(1, new Text("selected"));
        assertThat(union.getTag()).isEqualTo((byte) 1);
        assertThat(union.getObject()).isEqualTo(new Text("selected"));
        assertThat(union).hasToString("uniontype(1, selected)");

        assertThatThrownBy(() -> keyStruct.getFieldValue("missing"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void mapreduceOutputFormatCanSkipTemporaryDirectory() throws Exception {
        Configuration conf = new Configuration(false);
        Path outputPath = new Path("target/orc-output-" + UUID.randomUUID());
        conf.set(FileOutputFormat.OUTDIR, outputPath.toString());
        conf.setBoolean(org.apache.orc.mapreduce.OrcOutputFormat.SKIP_TEMP_DIRECTORY, true);

        TaskAttemptID attemptId = new TaskAttemptID("job", 1, TaskType.MAP, 0, 0);
        TaskAttemptContext context = new SimpleTaskAttemptContext(conf, attemptId);
        org.apache.orc.mapreduce.OrcOutputFormat<OrcStruct> outputFormat =
                new org.apache.orc.mapreduce.OrcOutputFormat<>();

        Path workFile = outputFormat.getDefaultWorkFile(context, ".orc");

        assertThat(workFile.getParent()).isEqualTo(outputPath);
        assertThat(workFile.getName()).startsWith("part-m-").endsWith(".orc");
        assertThat(workFile.toString()).doesNotContain("_temporary");
    }

    @Test
    void inputAndOutputFormatsApplyColumnSelectionAndConfiguration() {
        TypeDescription schema = TypeDescription.fromString(
                "struct<id:int,nested:struct<count:int,label:string>,tags:array<string>>");

        boolean[] include = org.apache.orc.mapred.OrcInputFormat.parseInclude(schema, "1,2");
        assertThat(include[0]).isTrue();
        assertThat(include[schema.findSubtype("id").getId()]).isFalse();
        assertAllIdsIncluded(include, schema.findSubtype("nested"));
        assertAllIdsIncluded(include, schema.findSubtype("tags"));

        boolean[] rootOnly = org.apache.orc.mapred.OrcInputFormat.parseInclude(schema, "");
        assertThat(rootOnly[0]).isTrue();
        assertThat(rootOnly[schema.findSubtype("nested").getId()]).isFalse();
        assertThat(org.apache.orc.mapred.OrcInputFormat.parseInclude(schema, null)).isNull();
        assertThat(org.apache.orc.mapred.OrcInputFormat.parseInclude(TypeDescription.createInt(), "0")).isNull();

        Configuration conf = new Configuration(false);
        OrcConf.MAPRED_OUTPUT_SCHEMA.setString(conf, schema.toString());
        assertThat(org.apache.orc.mapred.OrcOutputFormat.buildOptions(conf)).isNotNull();
        assertThat(new org.apache.orc.mapreduce.OrcInputFormat<OrcStruct>()).isNotNull();
        assertThat(new org.apache.orc.mapreduce.OrcOutputFormat<OrcStruct>()).isNotNull();
    }

    @Test
    void inputFormatsStoreSearchArgumentsForPredicatePushdown() {
        SearchArgument searchArgument = SearchArgumentFactory.newBuilder()
                .startAnd()
                .equals("id", PredicateLeaf.Type.LONG, 7L)
                .lessThan("label", PredicateLeaf.Type.STRING, "m")
                .end()
                .build();

        Configuration mapredConf = new Configuration(false);
        org.apache.orc.mapred.OrcInputFormat.setSearchArgument(mapredConf, searchArgument, new String[] {"id", "label"});

        assertThat(OrcConf.KRYO_SARG.getString(mapredConf)).isNotBlank();
        assertThat(OrcConf.SARG_COLUMNS.getString(mapredConf)).isEqualTo("id,label");

        Configuration mapreduceConf = new Configuration(false);
        org.apache.orc.mapreduce.OrcInputFormat.setSearchArgument(
                mapreduceConf, searchArgument, new String[] {"id", "label"});

        assertThat(OrcConf.KRYO_SARG.getString(mapreduceConf)).isNotBlank();
        assertThat(OrcConf.SARG_COLUMNS.getString(mapreduceConf)).isEqualTo("id,label");
    }

    private static TypeDescription complexSchema() {
        return TypeDescription.fromString("struct<"
                + "id:int,"
                + "name:string,"
                + "scores:array<int>,"
                + "attributes:map<string,string>,"
                + "choice:uniontype<int,string>,"
                + "nested:struct<active:boolean,amount:double>,"
                + "payload:binary,"
                + "event_time:timestamp"
                + ">");
    }

    private static OrcStruct createRow(TypeDescription schema, int id, String name,
            String unionText, boolean active) {
        OrcStruct row = new OrcStruct(schema);
        row.setFieldValue("id", new IntWritable(id));
        row.setFieldValue("name", new Text(name));
        row.setFieldValue("scores", intList(schema.findSubtype("scores"), id, id + 10));

        OrcMap<Text, Text> attributes = new OrcMap<>(schema.findSubtype("attributes"));
        attributes.put(new Text("name"), new Text(name));
        attributes.put(new Text("parity"), new Text(active ? "odd" : "even"));
        row.setFieldValue("attributes", attributes);

        OrcUnion choice = new OrcUnion(schema.findSubtype("choice"));
        choice.set(1, new Text(unionText));
        row.setFieldValue("choice", choice);

        OrcStruct nested = new OrcStruct(schema.findSubtype("nested"));
        nested.setFieldValue("active", new BooleanWritable(active));
        nested.setFieldValue("amount", new DoubleWritable(id + 0.5d));
        row.setFieldValue("nested", nested);

        row.setFieldValue("payload", new BytesWritable(("payload-" + id).getBytes(StandardCharsets.UTF_8)));
        row.setFieldValue("event_time", new OrcTimestamp("2024-01-0" + id + " 03:04:05.123456789"));
        return row;
    }

    private static OrcList<IntWritable> intList(TypeDescription listSchema, int first, int second) {
        OrcList<IntWritable> result = new OrcList<>(listSchema);
        result.add(new IntWritable(first));
        result.add(new IntWritable(second));
        return result;
    }

    private static void assertRow(OrcStruct row, int id, String name, String unionText, boolean active) {
        assertThat(row.getFieldValue("id")).isEqualTo(new IntWritable(id));
        assertThat(row.getFieldValue("name")).isEqualTo(new Text(name));

        OrcList<?> scores = (OrcList<?>) row.getFieldValue("scores");
        assertThat(scores.size()).isEqualTo(2);
        assertThat(scores.get(0)).isEqualTo(new IntWritable(id));
        assertThat(scores.get(1)).isEqualTo(new IntWritable(id + 10));

        OrcMap<?, ?> attributes = (OrcMap<?, ?>) row.getFieldValue("attributes");
        assertThat(attributes.get(new Text("name"))).isEqualTo(new Text(name));
        assertThat(attributes.get(new Text("parity"))).isEqualTo(new Text(active ? "odd" : "even"));

        OrcUnion choice = (OrcUnion) row.getFieldValue("choice");
        assertThat(choice.getTag()).isEqualTo((byte) 1);
        assertThat(choice.getObject()).isEqualTo(new Text(unionText));

        OrcStruct nested = (OrcStruct) row.getFieldValue("nested");
        assertThat(nested.getFieldValue("active")).isEqualTo(new BooleanWritable(active));
        assertThat(nested.getFieldValue("amount")).isEqualTo(new DoubleWritable(id + 0.5d));

        BytesWritable payload = (BytesWritable) row.getFieldValue("payload");
        byte[] bytes = new byte[payload.getLength()];
        System.arraycopy(payload.getBytes(), 0, bytes, 0, payload.getLength());
        assertThat(new String(bytes, StandardCharsets.UTF_8)).isEqualTo("payload-" + id);

        assertThat(row.getFieldValue("event_time")).isEqualTo(new OrcTimestamp(
                "2024-01-0" + id + " 03:04:05.123456789"));
    }

    private static void assertAllIdsIncluded(boolean[] include, TypeDescription type) {
        for (int id = type.getId(); id <= type.getMaximumId(); id++) {
            assertThat(include[id]).isTrue();
        }
    }

    private static OrcStruct copyRow(TypeDescription schema, VectorizedRowBatch batch, int sourceRow) {
        int rowIndex = batch.selectedInUse ? batch.selected[sourceRow] : sourceRow;
        OrcStruct result = new OrcStruct(schema);
        for (int field = 0; field < schema.getChildren().size(); field++) {
            result.setFieldValue(field, OrcMapredRecordReader.nextValue(
                    batch.cols[field], rowIndex, schema.getChildren().get(field), null));
        }
        return result;
    }

    private static final class SimpleTaskAttemptContext implements TaskAttemptContext {
        private final Configuration conf;
        private final TaskAttemptID attemptId;
        private String status = "";

        private SimpleTaskAttemptContext(Configuration conf, TaskAttemptID attemptId) {
            this.conf = conf;
            this.attemptId = attemptId;
        }

        @Override
        public Configuration getConfiguration() {
            return conf;
        }

        @Override
        public Credentials getCredentials() {
            return new Credentials();
        }

        @Override
        public JobID getJobID() {
            return attemptId.getJobID();
        }

        @Override
        public int getNumReduceTasks() {
            return 0;
        }

        @Override
        public Path getWorkingDirectory() throws IOException {
            return new Path(".");
        }

        @Override
        public Class<?> getOutputKeyClass() {
            return NullWritable.class;
        }

        @Override
        public Class<?> getOutputValueClass() {
            return OrcStruct.class;
        }

        @Override
        public Class<?> getMapOutputKeyClass() {
            return NullWritable.class;
        }

        @Override
        public Class<?> getMapOutputValueClass() {
            return OrcStruct.class;
        }

        @Override
        public String getJobName() {
            return "orc-mapreduce-test";
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
        public IntegerRanges getProfileTaskRange(boolean isMap) {
            return new IntegerRanges();
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
            return attemptId;
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
            return 0.0f;
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
        }
    }

    private static final class CapturingWriter implements Writer {
        private final TypeDescription schema;
        private final List<OrcStruct> rows = new ArrayList<>();
        private boolean closed;

        private CapturingWriter(TypeDescription schema) {
            this.schema = schema;
        }

        @Override
        public TypeDescription getSchema() {
            return schema;
        }

        @Override
        public void addUserMetadata(String name, ByteBuffer value) {
        }

        @Override
        public void addRowBatch(VectorizedRowBatch batch) {
            for (int row = 0; row < batch.size; row++) {
                rows.add(copyRow(schema, batch, row));
            }
        }

        @Override
        public void close() {
            closed = true;
        }

        @Override
        public long getRawDataSize() {
            return rows.size();
        }

        @Override
        public long getNumberOfRows() {
            return rows.size();
        }

        @Override
        public long writeIntermediateFooter() {
            return rows.size();
        }

        @Override
        public void appendStripe(byte[] stripe, int offset, int length, StripeInformation stripeInfo,
                org.apache.orc.OrcProto.StripeStatistics stripeStatistics) {
        }

        @Override
        public void appendStripe(byte[] stripe, int offset, int length, StripeInformation stripeInfo,
                StripeStatistics[] stripeStatistics) {
        }

        @Override
        public void appendUserMetadata(List<org.apache.orc.OrcProto.UserMetadataItem> userMetadata) {
        }

        @Override
        public ColumnStatistics[] getStatistics() {
            return new ColumnStatistics[0];
        }

        @Override
        public List<StripeInformation> getStripes() {
            return List.of();
        }

        @Override
        public long estimateMemory() {
            return 0;
        }
    }

    private static final class InMemoryRecordReader implements org.apache.orc.RecordReader {
        private final TypeDescription schema;
        private final List<OrcStruct> rows;
        private int position;

        private InMemoryRecordReader(TypeDescription schema, List<OrcStruct> rows) {
            this.schema = schema;
            this.rows = rows;
        }

        @Override
        public boolean nextBatch(VectorizedRowBatch batch) {
            batch.reset();
            int rowsToWrite = Math.min(batch.getMaxSize(), rows.size() - position);
            for (int row = 0; row < rowsToWrite; row++) {
                OrcStruct struct = rows.get(position++);
                for (int field = 0; field < schema.getChildren().size(); field++) {
                    OrcMapredRecordWriter.setColumn(schema.getChildren().get(field), batch.cols[field], row,
                            struct.getFieldValue(field));
                }
                batch.size++;
            }
            return rowsToWrite > 0;
        }

        @Override
        public long getRowNumber() {
            return position;
        }

        @Override
        public float getProgress() {
            return rows.isEmpty() ? 1.0f : (float) position / rows.size();
        }

        @Override
        public void close() {
        }

        @Override
        public void seekToRow(long rowCount) {
            position = (int) rowCount;
        }
    }
}
