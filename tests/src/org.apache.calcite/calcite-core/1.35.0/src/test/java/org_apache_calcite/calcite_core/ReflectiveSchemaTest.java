/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelReferentialConstraint;
import org.apache.calcite.rel.RelReferentialConstraintImpl;
import org.apache.calcite.rel.logical.LogicalTableScan;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.mapping.IntPair;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveSchemaTest {
    @Test
    void exposesPublicFieldsAsTablesAndMethodsAsFunctions() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:calcite:")) {
            CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
            SchemaPlus schema = calciteConnection.getRootSchema()
                    .add("reflective", new ReflectiveSchema(new Directory()));

            assertThat(schema.getFunctions("peopleByName")).hasSize(1);

            try (Statement statement = connection.createStatement();
                    ResultSet resultSet = statement.executeQuery(
                            "SELECT \"name\" FROM \"reflective\".\"people\"")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getString("name")).isEqualTo("Ada");
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

    public static class Directory {
        public final Person[] people = {new Person("Ada")};
        public final RelReferentialConstraint peopleReference =
                RelReferentialConstraintImpl.of(
                        List.of("people"), List.of("people"), List.of(IntPair.of(0, 0)));

        public TranslatableTable peopleByName() {
            return new PeopleTable();
        }
    }

    public static class PeopleTable extends AbstractTable implements TranslatableTable {
        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return typeFactory.builder().add("name", SqlTypeName.VARCHAR).build();
        }

        @Override
        public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable relOptTable) {
            return LogicalTableScan.create(context.getCluster(), relOptTable, List.of());
        }
    }

    public static class Person {
        public final String name;

        public Person(String name) {
            this.name = name;
        }
    }
}
