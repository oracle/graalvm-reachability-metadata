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
public class CustomRecordMappersInnerRecordCreatorTest {
    @Test
    void mapsRecordUsingStaticCreatorMethod() {
        List<ClickHouseColumn> columns = ClickHouseColumn.parse("id Int32, name String");
        Map<String, Integer> columnIndexes = new LinkedHashMap<>();
        columnIndexes.put("id", 0);
        columnIndexes.put("name", 1);
        ClickHouseValue[] values = new ClickHouseValue[] {
                ClickHouseIntegerValue.of(7),
                ClickHouseStringValue.of("Ford Prefect")
        };
        ClickHouseRecord record = ClickHouseSimpleRecord.of(columnIndexes, values);
        ClickHouseRecordMapper mapper = ClickHouseRecordMapper.of(null, columns, CreatorMappedRow.class);

        CreatorMappedRow row = mapper.mapTo(record, CreatorMappedRow.class);

        assertThat(row.getId()).isEqualTo(7);
        assertThat(row.getName()).isEqualTo("Ford Prefect");
    }

    public static final class CreatorMappedRow {
        private final int id;
        private final String name;

        private CreatorMappedRow(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public static CreatorMappedRow fromRecord(ClickHouseRecord record) {
            return new CreatorMappedRow(record.getValue("id").asInteger(), record.getValue("name").asString());
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
