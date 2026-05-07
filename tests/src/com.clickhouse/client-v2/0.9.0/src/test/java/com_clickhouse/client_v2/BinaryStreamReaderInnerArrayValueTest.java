/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.client_v2;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import com.clickhouse.client.api.data_formats.RowBinaryFormatReader;
import com.clickhouse.client.api.data_formats.internal.BinaryStreamReader;
import com.clickhouse.client.api.metadata.TableSchema;
import com.clickhouse.client.api.query.QuerySettings;
import com.clickhouse.data.ClickHouseColumn;
import com.clickhouse.data.ClickHouseDataType;
import com.clickhouse.data.format.BinaryStreamUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BinaryStreamReaderInnerArrayValueTest {
    @Test
    void readsPrimitiveArrayColumnAsIntArray() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BinaryStreamUtils.writeVarInt(output, 3);
        BinaryStreamUtils.writeInt32(output, 11);
        BinaryStreamUtils.writeInt32(output, 22);
        BinaryStreamUtils.writeInt32(output, 33);

        ClickHouseColumn column = ClickHouseColumn.of("numbers", ClickHouseDataType.Array, false,
                ClickHouseColumn.of("item", ClickHouseDataType.Int32, false, 0, 0));

        try (RowBinaryFormatReader reader = newReader(output.toByteArray(), column)) {
            assertThat(reader.next()).isNotNull();
            assertThat(reader.getIntArray("numbers")).containsExactly(11, 22, 33);
        }
    }

    @Test
    void readsNestedColumnAsListOfObjectArrays() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        BinaryStreamUtils.writeVarInt(output, 2);
        BinaryStreamUtils.writeString(output, "alpha");
        BinaryStreamUtils.writeInt32(output, 7);
        BinaryStreamUtils.writeString(output, "beta");
        BinaryStreamUtils.writeInt32(output, 13);

        ClickHouseColumn column = ClickHouseColumn.of("events", ClickHouseDataType.Nested, false,
                ClickHouseColumn.of("name", ClickHouseDataType.String, false, 0, 0),
                ClickHouseColumn.of("value", ClickHouseDataType.Int32, false, 0, 0));

        try (RowBinaryFormatReader reader = newReader(output.toByteArray(), column)) {
            assertThat(reader.next()).isNotNull();
            List<Object[]> events = reader.getList("events");

            assertThat(events).hasSize(2);
            assertThat(events.get(0)).containsExactly("alpha", 7);
            assertThat(events.get(1)).containsExactly("beta", 13);
        }
    }

    private static RowBinaryFormatReader newReader(byte[] rowBinaryBody, ClickHouseColumn column) throws IOException {
        QuerySettings settings = new QuerySettings().setUseTimeZone("UTC");
        TableSchema schema = new TableSchema(List.of(column));
        return new RowBinaryFormatReader(new ByteArrayInputStream(rowBinaryBody), settings, schema,
                new BinaryStreamReader.DefaultByteBufferAllocator());
    }
}
