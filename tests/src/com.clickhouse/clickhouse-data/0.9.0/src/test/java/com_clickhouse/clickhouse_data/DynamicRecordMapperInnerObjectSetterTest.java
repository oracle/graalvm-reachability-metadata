/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_clickhouse.clickhouse_data;

import static org.assertj.core.api.Assertions.assertThat;

import com.clickhouse.data.ClickHouseColumn;
import com.clickhouse.data.ClickHouseRecord;
import com.clickhouse.data.ClickHouseRecordMapper;
import com.clickhouse.data.ClickHouseSimpleRecord;
import com.clickhouse.data.ClickHouseValue;
import com.clickhouse.data.value.ClickHouseStringValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class DynamicRecordMapperInnerObjectSetterTest {
    @Test
    void mapsColumnThroughObjectSetter() {
        List<ClickHouseColumn> columns = ClickHouseColumn.parse("payload String");
        Map<String, Integer> columnIndexes = new LinkedHashMap<>();
        columnIndexes.put("payload", 0);
        ClickHouseValue[] values = new ClickHouseValue[] {
                ClickHouseStringValue.of("object setter value")
        };
        ClickHouseRecord record = ClickHouseSimpleRecord.of(columnIndexes, values);

        ClickHouseRecordMapper mapper = ClickHouseRecordMapper.of(null, columns, ObjectSetterRow.class);

        ObjectSetterRow row = mapper.mapTo(record, ObjectSetterRow.class);

        assertThat(row.getPayload()).isEqualTo("object setter value");
    }

    public static final class ObjectSetterRow {
        private Object payload;

        public ObjectSetterRow() {
        }

        public Object getPayload() {
            return payload;
        }

        public void setPayload(Object payload) {
            this.payload = payload;
        }
    }
}
