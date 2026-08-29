/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.avatica.ColumnMetaData;
import org.apache.calcite.avatica.util.Cursor;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.runtime.RecordEnumeratorCursor;
import org.junit.jupiter.api.Test;

import java.sql.Types;
import java.util.Calendar;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordEnumeratorCursorTest {
    @Test
    void readsPublicRecordFieldThroughAccessor() throws Exception {
        ColumnMetaData column = ColumnMetaData.dummy(
                ColumnMetaData.scalar(Types.INTEGER, "INTEGER", ColumnMetaData.Rep.PRIMITIVE_INT), false);
        try (RecordEnumeratorCursor<RowRecord> cursor = new RecordEnumeratorCursor<>(
                Linq4j.singletonEnumerable(new RowRecord(42)).enumerator(), RowRecord.class)) {
            List<Cursor.Accessor> accessors = cursor.createAccessors(
                    List.of(column), Calendar.getInstance(), null);

            assertThat(cursor.next()).isTrue();
            assertThat(accessors.get(0).getInt()).isEqualTo(42);
        }
    }

    public static class RowRecord {
        public final int value;

        public RowRecord(int value) {
            this.value = value;
        }
    }
}
