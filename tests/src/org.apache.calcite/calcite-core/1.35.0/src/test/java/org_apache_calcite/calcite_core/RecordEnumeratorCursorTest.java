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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordEnumeratorCursorTest {
    @Test
    void readsPublicFieldFromEnumeratorRecord() throws Exception {
        try (RecordEnumeratorCursor<RecordRow> cursor = new RecordEnumeratorCursor<>(
                Linq4j.singletonEnumerator(new RecordRow("calcite")), RecordRow.class)) {
            List<Cursor.Accessor> accessors = cursor.createAccessors(
                    Collections.singletonList(stringColumn()), Calendar.getInstance(), null);

            assertThat(accessors).hasSize(1);
            assertThat(cursor.next()).isTrue();
            assertThat(accessors.get(0).getString()).isEqualTo("calcite");
            assertThat(accessors.get(0).getObject()).isEqualTo("calcite");
            assertThat(cursor.next()).isFalse();
        }
    }

    private static ColumnMetaData stringColumn() {
        return ColumnMetaData.dummy(
                ColumnMetaData.scalar(Types.VARCHAR, "VARCHAR", ColumnMetaData.Rep.STRING), true);
    }

    public static final class RecordRow {
        public final String name;

        RecordRow(String name) {
            this.name = name;
        }
    }
}
