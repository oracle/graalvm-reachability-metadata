/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.client_v2;

import com.clickhouse.client.api.metadata.DefaultColumnToMethodMatchingStrategy;
import com.clickhouse.client.api.metadata.TableSchema;
import com.clickhouse.client.api.serde.POJOFieldDeserializer;
import com.clickhouse.client.api.serde.POJOSerDe;
import com.clickhouse.data.ClickHouseColumn;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class SerializerUtilsTest {
    @Test
    void registersPojoSetterDeserializerForMatchingTableColumn() {
        POJOSerDe serDe = new POJOSerDe(DefaultColumnToMethodMatchingStrategy.INSTANCE);
        TableSchema schema = new TableSchema("metrics", null, "default", List.of(ClickHouseColumn.of("count", "Int32")));

        try {
            serDe.registerClass(MetricRecord.class, schema);

            Map<String, POJOFieldDeserializer> deserializers = serDe.getFieldDeserializers(MetricRecord.class, schema);
            assertThat(deserializers).containsOnlyKeys("count");
            assertThat(deserializers.get("count")).isNotNull();
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        }
    }

    public static class MetricRecord {
        private int count;

        public int getCount() {
            return count;
        }

        public void setCount(int count) {
            this.count = count;
        }
    }
}
