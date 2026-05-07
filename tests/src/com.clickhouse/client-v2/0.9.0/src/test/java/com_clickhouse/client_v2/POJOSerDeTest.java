/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.client_v2;

import java.io.ByteArrayOutputStream;
import java.util.Map;

import com.clickhouse.client.api.metadata.DefaultColumnToMethodMatchingStrategy;
import com.clickhouse.client.api.metadata.TableSchema;
import com.clickhouse.client.api.serde.POJOFieldSerializer;
import com.clickhouse.client.api.serde.POJOSerDe;
import com.clickhouse.data.ClickHouseColumn;
import com.clickhouse.data.format.BinaryStreamUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class POJOSerDeTest {
    @Test
    void registeredFieldSerializersReadPojoValuesThroughGetters() throws Exception {
        TableSchema schema = new TableSchema(
                "events", null, "default", ClickHouseColumn.parse("id Int32, display_name String"));
        POJOSerDe serDe = new POJOSerDe(DefaultColumnToMethodMatchingStrategy.INSTANCE);
        serDe.registerClass(EventRow.class, schema);

        Map<String, POJOFieldSerializer> serializers = serDe.getFieldSerializers(EventRow.class, schema);
        EventRow row = new EventRow(42, "native image");
        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        serializers.get("id").serialize(row, actual);
        serializers.get("display_name").serialize(row, actual);

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        BinaryStreamUtils.writeInt32(expected, 42);
        BinaryStreamUtils.writeString(expected, "native image");
        assertThat(serializers).containsOnlyKeys("id", "display_name");
        assertThat(actual.toByteArray()).isEqualTo(expected.toByteArray());
    }

    public static final class EventRow {
        private final int id;
        private final String displayName;

        public EventRow(int id, String displayName) {
            this.id = id;
            this.displayName = displayName;
        }

        public int getId() {
            return id;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
