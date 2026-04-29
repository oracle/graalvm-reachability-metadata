/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.interpreter.Interpreters;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.RelBuilder;
import org.junit.jupiter.api.Test;

public class TableScanNodeTest {
    @Test
    void interpreterScansQueryableTableWithPublicFieldRows() {
        RosterTable table = new RosterTable(List.of(
                new RosterEntry("Ada"),
                new RosterEntry("Grace")));
        SchemaPlus rootSchema = Frameworks.createRootSchema(true);
        rootSchema.add("ROSTER", table);
        RelBuilder builder = newRelBuilder(rootSchema);
        RelNode tableScan = builder.scan("ROSTER").build();

        List<Object[]> rows = Interpreters.bindable(tableScan)
                .bind(new SchemaDataContext(rootSchema, (JavaTypeFactory) builder.getTypeFactory()))
                .toList();

        assertThat(rows)
                .extracting(row -> row[0])
                .containsExactly("Ada", "Grace");
    }

    private static RelBuilder newRelBuilder(SchemaPlus rootSchema) {
        FrameworkConfig config = Frameworks.newConfigBuilder()
                .defaultSchema(rootSchema)
                .build();
        return RelBuilder.create(config);
    }

    public static class RosterEntry {
        public final String name;

        RosterEntry(String name) {
            this.name = name;
        }
    }

    private static class RosterTable extends AbstractQueryableTable {
        private final List<RosterEntry> entries;

        RosterTable(List<RosterEntry> entries) {
            super(RosterEntry.class);
            this.entries = entries;
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return ((JavaTypeFactory) typeFactory).createType(RosterEntry.class);
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> Queryable<T> asQueryable(QueryProvider queryProvider, SchemaPlus schema, String tableName) {
            return (Queryable<T>) Linq4j.asEnumerable(entries).asQueryable();
        }
    }

    private static class SchemaDataContext implements DataContext {
        private final SchemaPlus rootSchema;
        private final JavaTypeFactory typeFactory;

        SchemaDataContext(SchemaPlus rootSchema, JavaTypeFactory typeFactory) {
            this.rootSchema = rootSchema;
            this.typeFactory = typeFactory;
        }

        @Override
        public SchemaPlus getRootSchema() {
            return rootSchema;
        }

        @Override
        public JavaTypeFactory getTypeFactory() {
            return typeFactory;
        }

        @Override
        public QueryProvider getQueryProvider() {
            return null;
        }

        @Override
        public Object get(String name) {
            return null;
        }
    }
}
