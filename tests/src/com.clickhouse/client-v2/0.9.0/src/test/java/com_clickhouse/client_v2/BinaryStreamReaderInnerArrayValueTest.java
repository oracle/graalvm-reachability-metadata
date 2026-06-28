/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.client_v2;

import com.clickhouse.client.api.data_formats.RowBinaryWithNamesAndTypesFormatReader;
import com.clickhouse.client.api.data_formats.internal.BinaryStreamReader;
import com.clickhouse.client.api.query.QuerySettings;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BinaryStreamReaderInnerArrayValueTest {
    @Test
    void rowBinaryReaderReadsPrimitiveAndTupleArrays() throws Exception {
        try (RowBinaryWithNamesAndTypesFormatReader reader = new RowBinaryWithNamesAndTypesFormatReader(
                new ByteArrayInputStream(rowBinaryWithPrimitiveAndTupleArrays()),
                new QuerySettings().setUseTimeZone("UTC"),
                new BinaryStreamReader.DefaultByteBufferAllocator())) {

            assertThat(reader.hasNext()).isTrue();
            reader.next();

            assertThat(reader.getIntArray("numbers")).containsExactly(10, 20, 30);

            List<Object[]> tuples = reader.getList("tuples");
            assertThat(tuples).hasSize(2);
            assertThat(tuples.get(0)).containsExactly(1, "one");
            assertThat(tuples.get(1)).containsExactly(2, "two");
            assertThat(reader.hasNext()).isFalse();
        }
    }

    private static byte[] rowBinaryWithPrimitiveAndTupleArrays() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writeVarInt(output, 2);
        writeString(output, "numbers");
        writeString(output, "tuples");
        writeString(output, "Array(Int32)");
        writeString(output, "Array(Tuple(Int32, String))");

        writeVarInt(output, 3);
        writeInt32(output, 10);
        writeInt32(output, 20);
        writeInt32(output, 30);

        writeVarInt(output, 2);
        writeInt32(output, 1);
        writeString(output, "one");
        writeInt32(output, 2);
        writeString(output, "two");
        return output.toByteArray();
    }

    private static void writeString(OutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(output, bytes.length);
        output.write(bytes);
    }

    private static void writeInt32(OutputStream output, int value) throws IOException {
        output.write(value & 0xFF);
        output.write((value >>> 8) & 0xFF);
        output.write((value >>> 16) & 0xFF);
        output.write((value >>> 24) & 0xFF);
    }

    private static void writeVarInt(OutputStream output, int value) throws IOException {
        int remaining = value;
        while ((remaining & ~0x7F) != 0) {
            output.write((remaining & 0x7F) | 0x80);
            remaining >>>= 7;
        }
        output.write(remaining);
    }
}
