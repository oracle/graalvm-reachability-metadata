/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.calcite.DataContexts;
import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.Table;
import org.junit.jupiter.api.Test;

public class ReflectiveSchemaInnerFieldSelectorTest {
    @Test
    void scanProjectsPublicFieldsFromElementObjects() {
        ReflectiveSchema schema = new ReflectiveSchema(new LibrarySchema());

        Table table = schema.getTable("BOOKS");
        assertThat(table).isInstanceOf(ScannableTable.class);

        List<Object[]> rows = ((ScannableTable) table).scan(DataContexts.EMPTY).toList();

        assertThat(rows)
                .extracting(row -> row[0])
                .containsExactly("Learning Calcite", "Query Planning");
    }

    public static class LibrarySchema {
        public final Book[] BOOKS = {
                new Book("Learning Calcite"),
                new Book("Query Planning")
        };
    }

    public static class Book {
        public final String title;

        Book(String title) {
            this.title = title;
        }
    }
}
