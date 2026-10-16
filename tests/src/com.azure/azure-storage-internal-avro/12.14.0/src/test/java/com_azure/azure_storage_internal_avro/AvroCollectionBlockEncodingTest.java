/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_storage_internal_avro;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.storage.internal.avro.implementation.AvroObject;
import com.azure.storage.internal.avro.implementation.AvroReaderSyncFactory;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

public class AvroCollectionBlockEncodingTest {
    private static final byte[] SYNC_MARKER = {
        2, 4, 6, 8, 10, 12, 14, 16, 18, 20, 22, 24, 26, 28, 30, 32
    };

    @Test
    void readsArraySplitAcrossSizeDelimitedBlocks() {
        ByteArrayOutputStream array = new ByteArrayOutputStream();
        writeSizeDelimitedBlock(
                array,
                2,
                items -> {
                    writeLong(items, 10);
                    writeLong(items, 20);
                });
        writeSizeDelimitedBlock(array, 1, items -> writeLong(items, 30));
        writeLong(array, 0);

        Object value = readSingleObject(
                createContainer("{\"type\":\"array\",\"items\":\"long\"}", array.toByteArray()));

        assertThat(value).isEqualTo(List.of(10L, 20L, 30L));
    }

    @Test
    void readsMapSplitAcrossSizeDelimitedBlocks() {
        ByteArrayOutputStream map = new ByteArrayOutputStream();
        writeSizeDelimitedBlock(
                map,
                2,
                entries -> {
                    writeString(entries, "first");
                    writeLong(entries, 1);
                    writeString(entries, "second");
                    writeLong(entries, 2);
                });
        writeSizeDelimitedBlock(
                map,
                1,
                entries -> {
                    writeString(entries, "third");
                    writeLong(entries, 3);
                });
        writeLong(map, 0);

        Object value = readSingleObject(createContainer("{\"type\":\"map\",\"values\":\"long\"}", map.toByteArray()));

        assertThat(value).isEqualTo(Map.of("first", 1L, "second", 2L, "third", 3L));
    }

    private static Object readSingleObject(byte[] file) {
        List<AvroObject> objects = new ArrayList<>();
        new AvroReaderSyncFactory().getAvroReader(ByteBuffer.wrap(file)).read().forEach(objects::add);

        assertThat(objects).hasSize(1);
        return objects.get(0).getObject();
    }

    private static byte[] createContainer(String schema, byte[] datum) {
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(new byte[] {'O', 'b', 'j', 1});
        writeLong(file, 2);
        writeString(file, "avro.schema");
        writeBytes(file, schema.getBytes(StandardCharsets.UTF_8));
        writeString(file, "avro.codec");
        writeBytes(file, "null".getBytes(StandardCharsets.UTF_8));
        writeLong(file, 0);
        file.writeBytes(SYNC_MARKER);
        writeLong(file, 1);
        writeLong(file, datum.length);
        file.writeBytes(datum);
        file.writeBytes(SYNC_MARKER);
        return file.toByteArray();
    }

    private static void writeSizeDelimitedBlock(
            ByteArrayOutputStream output, int itemCount, Consumer<ByteArrayOutputStream> writeItems) {
        ByteArrayOutputStream items = new ByteArrayOutputStream();
        writeItems.accept(items);
        writeLong(output, -itemCount);
        writeLong(output, items.size());
        output.writeBytes(items.toByteArray());
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
}
