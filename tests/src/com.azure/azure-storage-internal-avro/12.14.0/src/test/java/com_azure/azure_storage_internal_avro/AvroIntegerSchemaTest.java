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
import org.junit.jupiter.api.Test;

public class AvroIntegerSchemaTest {
    private static final byte[] SYNC_MARKER = {
        15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0
    };

    @Test
    void readsTopLevelIntegerValuesFromAnObjectContainer() {
        List<Integer> expected = List.of(-1_000_000, -1, 0, 1, 1_000_000);
        byte[] file = createIntegerContainer(expected);

        List<AvroObject> objects = new ArrayList<>();
        new AvroReaderSyncFactory().getAvroReader(ByteBuffer.wrap(file)).read().forEach(objects::add);

        assertThat(objects).extracting(AvroObject::getObject).containsExactlyElementsOf(expected);
        assertThat(objects).allSatisfy(object -> assertThat(object.getObject()).isInstanceOf(Integer.class));
    }

    private static byte[] createIntegerContainer(List<Integer> values) {
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        header.writeBytes(new byte[] {'O', 'b', 'j', 1});
        writeLong(header, 1);
        writeString(header, "avro.schema");
        writeBytes(header, "\"int\"".getBytes(StandardCharsets.UTF_8));
        writeLong(header, 0);
        header.writeBytes(SYNC_MARKER);

        ByteArrayOutputStream records = new ByteArrayOutputStream();
        values.forEach(value -> writeLong(records, value));

        ByteArrayOutputStream file = new ByteArrayOutputStream();
        file.writeBytes(header.toByteArray());
        writeLong(file, values.size());
        writeLong(file, records.size());
        file.writeBytes(records.toByteArray());
        file.writeBytes(SYNC_MARKER);
        return file.toByteArray();
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
