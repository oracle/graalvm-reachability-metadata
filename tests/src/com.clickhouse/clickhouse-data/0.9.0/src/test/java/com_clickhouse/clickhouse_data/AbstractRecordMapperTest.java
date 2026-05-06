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
import com.clickhouse.data.value.ClickHouseIntegerValue;
import com.clickhouse.data.value.ClickHouseStringValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

@SuppressWarnings("deprecation")
public class AbstractRecordMapperTest {
    @Test
    void mapsRecordToPojoUsingDefaultConstructorAndSetters() {
        List<ClickHouseColumn> columns = ClickHouseColumn.parse("id Int32, display_name String, raw_payload String");
        Map<String, Integer> columnIndexes = new LinkedHashMap<>();
        columnIndexes.put("id", 0);
        columnIndexes.put("display_name", 1);
        columnIndexes.put("raw_payload", 2);
        ClickHouseValue[] values = new ClickHouseValue[] {
                ClickHouseIntegerValue.of(42),
                ClickHouseStringValue.of("Arthur Dent"),
                ClickHouseStringValue.of("mostly harmless")
        };
        ClickHouseRecord record = ClickHouseSimpleRecord.of(columnIndexes, values);

        ClickHouseRecordMapper mapper = ClickHouseRecordMapper.of(null, columns, MappedClickHouseRow.class);

        MappedClickHouseRow row = mapper.mapTo(record, MappedClickHouseRow.class);

        assertThat(row.getId()).isEqualTo(42);
        assertThat(row.getDisplayName()).isEqualTo("Arthur Dent");
        assertThat(row.getRawPayload()).isEqualTo("mostly harmless");
    }

    public static final class MappedClickHouseRow {
        private int id;
        private String displayName;
        private String rawPayload;

        public MappedClickHouseRow() {
        }

        public int getId() {
            return id;
        }

        public void setId(int id) {
            this.id = id;
        }

        public String getDisplayName() {
            return displayName;
        }

        public void setDisplayName(String displayName) {
            this.displayName = displayName;
        }

        public String getRawPayload() {
            return rawPayload;
        }

        public void setRawPayload(String rawPayload) {
            this.rawPayload = rawPayload;
        }
    }
}
