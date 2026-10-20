/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_kylin.kylin_jdbc;

import java.sql.Struct;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.ColumnMetaData;
import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.MetaImpl;
import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.util.ArrayIteratorCursor;
import org.apache.kylin.jdbc.shaded.org.apache.calcite.avatica.util.Cursor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractCursorInnerStructGetterTest {
    @Test
    void readsACompositeStructFromPublicFields() throws Exception {
        StructRow struct = new StructRow("north", 9);
        ArrayIteratorCursor cursor =
                new ArrayIteratorCursor(Collections.singletonList(new Object[] {struct}).iterator());
        List<ColumnMetaData> fields = Arrays.asList(
                MetaImpl.columnMetaData("region", 0, String.class, false),
                MetaImpl.columnMetaData("quantity", 1, int.class, false));
        ColumnMetaData structColumn = ColumnMetaData.dummy(ColumnMetaData.struct(fields), true);
        List<Cursor.Accessor> accessors =
                cursor.createAccessors(Collections.singletonList(structColumn), Calendar.getInstance(), null);

        assertThat(cursor.next()).isTrue();
        Struct value = (Struct) accessors.get(0).getObject();
        assertThat(value.getAttributes()).containsExactly("north", 9);
        cursor.close();
    }

    public static class StructRow {
        public String region;
        public int quantity;

        public StructRow(String region, int quantity) {
            this.region = region;
            this.quantity = quantity;
        }
    }
}
