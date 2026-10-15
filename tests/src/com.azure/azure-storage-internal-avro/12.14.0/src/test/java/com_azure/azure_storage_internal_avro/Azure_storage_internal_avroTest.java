/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_storage_internal_avro;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.storage.internal.avro.implementation.AvroObject;
import com.azure.storage.internal.avro.implementation.AvroReaderFactory;
import com.azure.storage.internal.avro.implementation.AvroReaderSyncFactory;
import com.azure.storage.internal.avro.implementation.schema.primitive.AvroNullSchema;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

public class Azure_storage_internal_avroTest {
    private static final byte[] SYNC_MARKER = {
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15
    };
    private static final String SCHEMA =
            """
            {"type":"record","name":"Event","fields":[
              {"name":"name","type":"string"},
              {"name":"count","type":"long"},
              {"name":"active","type":"boolean"},
              {"name":"ratio","type":"float"},
              {"name":"score","type":"double"},
              {"name":"payload","type":"bytes"},
              {"name":"token","type":{"type":"fixed","name":"Token","size":3}},
              {"name":"status","type":{"type":"enum","name":"Status","symbols":["NEW","DONE"]}},
              {"name":"tags","type":{"type":"array","items":"string"}},
              {"name":"attributes","type":{"type":"map","values":"long"}},
              {"name":"note","type":["null","string"]}
            ]}
            """;

    @Test
    void parsesObjectContainerWithAllSupportedSchemaShapesSynchronously() {
        AvroFixture fixture = createFixture();

        List<AvroObject> objects = new ArrayList<>();
        new AvroReaderSyncFactory().getAvroReader(ByteBuffer.wrap(fixture.file())).read().forEach(objects::add);

        assertThat(objects).hasSize(2);
        assertFirstRecord(objects.get(0));
        assertSecondRecord(objects.get(1));
        assertOffsets(objects, fixture);
    }

    @Test
    void parsesObjectContainerAcrossArbitraryReactiveBufferBoundaries() {
        AvroFixture fixture = createFixture();

        List<AvroObject> objects =
                new AvroReaderFactory()
                        .getAvroReader(fragment(fixture.file()))
                        .read()
                        .collectList()
                        .block(Duration.ofSeconds(30));

        assertThat(objects).isNotNull().hasSize(2);
        assertFirstRecord(objects.get(0));
        assertSecondRecord(objects.get(1));
        assertOffsets(objects, fixture);
    }

    @Test
    void resumesReactiveReadingAtAnObjectWithinASeparatelySuppliedBlock() {
        AvroFixture fixture = createFixture();

        List<AvroObject> objects =
                new AvroReaderFactory()
                        .getAvroReader(
                                fragment(fixture.header()),
                                fragment(fixture.body()),
                                fixture.header().length,
                                1)
                        .read()
                        .collectList()
                        .block(Duration.ofSeconds(30));

        assertThat(objects).isNotNull().hasSize(1);
        assertSecondRecord(objects.get(0));
        assertThat(objects.get(0).getBlockOffset()).isEqualTo(fixture.header().length);
        assertThat(objects.get(0).getObjectIndex()).isEqualTo(1);
        assertThat(objects.get(0).getNextBlockOffset()).isEqualTo(fixture.file().length);
        assertThat(objects.get(0).getNextObjectIndex()).isZero();
    }

    @Test
    void resumesSynchronousReadingAtAnObjectWithinASeparatelySuppliedBlock() {
        AvroFixture fixture = createFixture();

        List<AvroObject> objects = new ArrayList<>();
        new AvroReaderSyncFactory()
                .getAvroReader(
                        ByteBuffer.wrap(fixture.header()),
                        ByteBuffer.wrap(fixture.file()),
                        fixture.header().length,
                        1)
                .read()
                .forEach(objects::add);

        assertThat(objects).hasSize(1);
        assertSecondRecord(objects.get(0));
        assertThat(objects.get(0).getObjectIndex()).isEqualTo(1);
    }

    private static void assertFirstRecord(AvroObject avroObject) {
        Map<String, Object> record = asRecord(avroObject);
        assertThat(record)
                .containsEntry("$record", "Event")
                .containsEntry("name", "alpha")
                .containsEntry("count", 7L)
                .containsEntry("active", true)
                .containsEntry("ratio", 1.25F)
                .containsEntry("score", 9.5D)
                .containsEntry("status", "DONE")
                .containsEntry("tags", List.of("red", "blue"))
                .containsEntry("attributes", Map.of("left", 11L, "right", 12L))
                .containsEntry("note", "present");
        assertThat(readBytes(record.get("payload"))).containsExactly(3, 1, 4);
        assertThat(readBytes(record.get("token"))).containsExactly(8, 6, 7);
    }

    private static void assertSecondRecord(AvroObject avroObject) {
        Map<String, Object> record = asRecord(avroObject);
        assertThat(record)
                .containsEntry("$record", "Event")
                .containsEntry("name", "beta")
                .containsEntry("count", 42L)
                .containsEntry("active", false)
                .containsEntry("ratio", -2.5F)
                .containsEntry("score", 0.125D)
                .containsEntry("status", "NEW")
                .containsEntry("tags", List.of())
                .containsEntry("attributes", Map.of());
        assertThat(record.get("note")).isInstanceOf(AvroNullSchema.Null.class);
        assertThat(readBytes(record.get("payload"))).isEmpty();
        assertThat(readBytes(record.get("token"))).containsExactly(1, 2, 3);
    }

    private static byte[] readBytes(Object value) {
        assertThat(value).isInstanceOf(List.class);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        for (Object element : (List<?>) value) {
            assertThat(element).isInstanceOf(ByteBuffer.class);
            ByteBuffer buffer = ((ByteBuffer) element).duplicate();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            output.writeBytes(bytes);
        }
        return output.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asRecord(AvroObject avroObject) {
        assertThat(avroObject.getObject()).isInstanceOf(Map.class);
        return (Map<String, Object>) avroObject.getObject();
    }

    private static void assertOffsets(List<AvroObject> objects, AvroFixture fixture) {
        AvroObject first = objects.get(0);
        AvroObject second = objects.get(1);
        assertThat(first.getBlockOffset()).isEqualTo(fixture.header().length);
        assertThat(first.getObjectIndex()).isZero();
        assertThat(first.getNextBlockOffset()).isEqualTo(fixture.header().length);
        assertThat(first.getNextObjectIndex()).isEqualTo(1);
        assertThat(second.getBlockOffset()).isEqualTo(fixture.header().length);
        assertThat(second.getObjectIndex()).isEqualTo(1);
        assertThat(second.getNextBlockOffset()).isEqualTo(fixture.file().length);
        assertThat(second.getNextObjectIndex()).isZero();
    }

    private static AvroFixture createFixture() {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.writeBytes(new byte[] {'O', 'b', 'j', 1});
        writeLong(header, 2);
        writeString(header, "avro.schema");
        writeBytes(header, SCHEMA.getBytes(StandardCharsets.UTF_8));
        writeString(header, "avro.codec");
        writeBytes(header, "null".getBytes(StandardCharsets.UTF_8));
        writeLong(header, 0);
        header.writeBytes(SYNC_MARKER);

        ByteArrayOutputStream records = new ByteArrayOutputStream();
        writeRecord(
                records,
                "alpha",
                7,
                true,
                1.25F,
                9.5D,
                new byte[] {3, 1, 4},
                new byte[] {8, 6, 7},
                1,
                List.of("red", "blue"),
                Map.of("left", 11L, "right", 12L),
                "present");
        writeRecord(
                records,
                "beta",
                42,
                false,
                -2.5F,
                0.125D,
                new byte[0],
                new byte[] {1, 2, 3},
                0,
                List.of(),
                Map.of(),
                null);

        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeLong(body, 2);
        writeLong(body, records.size());
        body.writeBytes(records.toByteArray());
        body.writeBytes(SYNC_MARKER);

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(header.toByteArray());
        file.writeBytes(body.toByteArray());
        return new AvroFixture(header.toByteArray(), body.toByteArray(), file.toByteArray());
    }

    private static void writeRecord(
            ByteArrayOutputStream output,
            String name,
            long count,
            boolean active,
            float ratio,
            double score,
            byte[] payload,
            byte[] token,
            int status,
            List<String> tags,
            Map<String, Long> attributes,
            String note) {
        writeString(output, name);
        writeLong(output, count);
        output.write(active ? 1 : 0);
        output.writeBytes(ByteBuffer.allocate(Float.BYTES).order(ByteOrder.LITTLE_ENDIAN).putFloat(ratio).array());
        output.writeBytes(ByteBuffer.allocate(Double.BYTES).order(ByteOrder.LITTLE_ENDIAN).putDouble(score).array());
        writeBytes(output, payload);
        output.writeBytes(token);
        writeLong(output, status);
        writeLong(output, tags.size());
        tags.forEach(tag -> writeString(output, tag));
        if (!tags.isEmpty()) {
            writeLong(output, 0);
        }
        writeLong(output, attributes.size());
        attributes.forEach(
                (key, value) -> {
                    writeString(output, key);
                    writeLong(output, value);
                });
        if (!attributes.isEmpty()) {
            writeLong(output, 0);
        }
        writeLong(output, note == null ? 0 : 1);
        if (note != null) {
            writeString(output, note);
        }
    }

    private static void writeString(ByteArrayOutputStream output, String value) {
        writeBytes(output, value.getBytes(StandardCharsets.UTF_8));
    }

    private static void writeBytes(ByteArrayOutputStream output, byte[] value) {
        writeLong(output, value.length);
        output.writeBytes(value);
    }

    private static void writeLong(ByteArrayOutputStream output, long value) {
        long encoded = (value << 1) ^ (value >> 63);
        while ((encoded & ~0x7FL) != 0) {
            output.write((int) (encoded & 0x7F) | 0x80);
            encoded >>>= 7;
        }
        output.write((int) encoded);
    }

    private static Flux<ByteBuffer> fragment(byte[] bytes) {
        List<ByteBuffer> fragments = new ArrayList<>();
        int offset = 0;
        int length = 1;
        while (offset < bytes.length) {
            int end = Math.min(bytes.length, offset + length);
            fragments.add(ByteBuffer.wrap(bytes, offset, end - offset).slice());
            offset = end;
            length = length == 7 ? 1 : length + 1;
        }
        return Flux.fromIterable(fragments);
    }

    private record AvroFixture(byte[] header, byte[] body, byte[] file) {}
}
