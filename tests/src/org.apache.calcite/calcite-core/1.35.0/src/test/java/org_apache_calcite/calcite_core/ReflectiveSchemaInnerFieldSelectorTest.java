/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.DataContexts;
import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.schema.ScannableTable;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveSchemaInnerFieldSelectorTest {
    @Test
    void scansRecordFieldsAsRows() {
        ReflectiveSchema schema = new ReflectiveSchema(new Directory());
        ScannableTable table = (ScannableTable) schema.getTable("people");

        List<Object[]> rows = table.scan(DataContexts.EMPTY).toList();

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)).containsExactlyInAnyOrder(7, "Ada");
    }

    public static class Directory {
        public final Person[] people = {new Person(7, "Ada")};
    }

    public static class Person {
        public final int id;
        public final String name;

        public Person(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }
}
