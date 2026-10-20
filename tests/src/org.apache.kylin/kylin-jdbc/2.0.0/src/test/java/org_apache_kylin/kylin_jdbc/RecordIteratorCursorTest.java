/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.ColumnMetaData;
import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.MetaImpl;
import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.util.Cursor;
import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.util.RecordIteratorCursor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordIteratorCursorTest {
    @Test
    void readsValuesFromPublicRecordFields() throws Exception {
        RecordIteratorCursor<Row> cursor =
                new RecordIteratorCursor<>(Collections.singletonList(new Row("active")).iterator(), Row.class);
        List<ColumnMetaData> columns =
                Collections.singletonList(MetaImpl.columnMetaData("state", 0, String.class, false));
        List<Cursor.Accessor> accessors = cursor.createAccessors(columns, Calendar.getInstance(), null);

        assertThat(cursor.next()).isTrue();
        assertThat(accessors.get(0).getObject()).isEqualTo("active");
        cursor.close();
    }

    public static class Row {
        public String state;

        public Row(String state) {
            this.state = state;
        }
    }
}
