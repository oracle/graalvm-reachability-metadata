/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.util.Calendar;
import java.util.List;
import org.apache.calcite.avatica.ColumnMetaData;
import org.apache.calcite.avatica.util.Cursor;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.runtime.RecordEnumeratorCursor;
import org.junit.jupiter.api.Test;

public class RecordEnumeratorCursorTest {
    @Test
    void createsAccessorsForPublicRecordFields() throws Exception {
        RecordEnumeratorCursor<PersonRecord> cursor = new RecordEnumeratorCursor<>(
                Linq4j.singletonEnumerator(new PersonRecord("Ada")),
                PersonRecord.class);
        List<Cursor.Accessor> accessors = cursor.createAccessors(
                List.of(column("name", ColumnMetaData.Rep.STRING)),
                Calendar.getInstance(),
                null);

        assertThat(cursor.next()).isTrue();
        assertThat(accessors.get(0).getObject()).isEqualTo("Ada");
        assertThat(accessors.get(0).getString()).isEqualTo("Ada");
        cursor.close();
    }

    private static ColumnMetaData column(String name, ColumnMetaData.Rep representation) {
        ColumnMetaData.AvaticaType type = ColumnMetaData.scalar(Types.VARCHAR, "VARCHAR", representation);
        return new ColumnMetaData(
                0,
                false,
                true,
                true,
                false,
                ResultSetMetaData.columnNullable,
                false,
                255,
                name,
                name,
                null,
                255,
                0,
                null,
                null,
                type,
                true,
                false,
                false,
                String.class.getName());
    }

    public static class PersonRecord {
        public final String name;

        PersonRecord(String name) {
            this.name = name;
        }
    }
}
