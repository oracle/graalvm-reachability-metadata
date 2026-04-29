/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.interpreter.Interpreters;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.linq4j.Queryable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.runtime.ArrayBindable;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.RelBuilder;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TableScanNodeTest {
    @Test
    public void bindableTableScanReadsPublicFieldsFromQueryableRows() {
        SchemaPlus rootSchema = Frameworks.createRootSchema(true);
        rootSchema.add("PEOPLE", new PeopleTable());
        RelNode scan = RelBuilder.create(
            Frameworks.newConfigBuilder().defaultSchema(rootSchema).build())
            .scan("PEOPLE")
            .build();

        ArrayBindable bindable = Interpreters.bindable(scan);
        Enumerator<Object[]> enumerator = bindable.bind(new TestDataContext(rootSchema))
            .enumerator();
        try {
            assertThat(enumerator.moveNext()).isTrue();
            assertThat(enumerator.current()).containsExactly(1, "Ada");

            assertThat(enumerator.moveNext()).isTrue();
            assertThat(enumerator.current()).containsExactly(2, "Grace");
            assertThat(enumerator.moveNext()).isFalse();
        } finally {
            enumerator.close();
        }
    }

    private static final class TestDataContext implements DataContext {
        private final SchemaPlus rootSchema;
        private final JavaTypeFactory typeFactory = new JavaTypeFactoryImpl();

        private TestDataContext(SchemaPlus rootSchema) {
            this.rootSchema = rootSchema;
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
            return Linq4j.DEFAULT_PROVIDER;
        }

        @Override
        public Object get(String name) {
            return null;
        }
    }

    private static final class PeopleTable extends AbstractQueryableTable {
        private static final List<PersonRow> ROWS = Arrays.asList(
            new PersonRow(1, "Ada"),
            new PersonRow(2, "Grace"));

        private PeopleTable() {
            super(PersonRow.class);
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> Queryable<T> asQueryable(QueryProvider queryProvider,
            SchemaPlus schema, String tableName) {
            return (Queryable<T>) Linq4j.asEnumerable(ROWS).asQueryable();
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return typeFactory.builder()
                .add("ID", SqlTypeName.INTEGER)
                .add("NAME", SqlTypeName.VARCHAR)
                .build();
        }
    }

    public static final class PersonRow {
        public final int ID;
        public final String NAME;

        public PersonRow(int id, String name) {
            this.ID = id;
            this.NAME = name;
        }
    }
}
