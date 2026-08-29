/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.DataContexts;
import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.interpreter.Interpreter;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.jdbc.Driver;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.impl.AbstractTableQueryable;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.RelBuilder;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class TableScanNodeTest {
    @Test
    void scansPublicFieldsFromQueryableRows() throws Exception {
        try (Connection connection = new Driver().connect("jdbc:calcite:", new Properties())) {
            CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
            SchemaPlus rootSchema = calciteConnection.getRootSchema();
            rootSchema.add("rows", new EntriesTable(List.of(new Entry(4), new Entry(9))));
            RelBuilder builder = RelBuilder.create(
                    Frameworks.newConfigBuilder().defaultSchema(rootSchema).build());
            RelNode scan = builder.scan("rows").build();

            try (Interpreter interpreter = new Interpreter(
                    DataContexts.of(calciteConnection, rootSchema), scan)) {
                List<Object[]> rows = interpreter.toList();

                assertThat(rows).hasSize(2);
                assertThat(rows.get(0)).containsExactly(4);
                assertThat(rows.get(1)).containsExactly(9);
            }
        }
    }

    public static class EntriesTable extends AbstractQueryableTable {
        private final List<Entry> entries;

        public EntriesTable(List<Entry> entries) {
            super(Entry.class);
            this.entries = entries;
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return ((JavaTypeFactory) typeFactory).createType(Entry.class);
        }

        @Override
        public <T> Queryable<T> asQueryable(
                QueryProvider queryProvider, SchemaPlus schema, String tableName) {
            return new AbstractTableQueryable<T>(queryProvider, schema, this, tableName) {
                @SuppressWarnings("unchecked")
                @Override
                public Enumerator<T> enumerator() {
                    return (Enumerator<T>) Linq4j.iterableEnumerator(entries);
                }
            };
        }
    }

    public static class Entry {
        public final int value;

        public Entry(int value) {
            this.value = value;
        }
    }
}
