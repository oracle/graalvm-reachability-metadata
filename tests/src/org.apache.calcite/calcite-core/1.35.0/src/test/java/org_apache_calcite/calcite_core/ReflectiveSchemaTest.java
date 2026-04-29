/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelReferentialConstraint;
import org.apache.calcite.rel.RelReferentialConstraintImpl;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.TableMacro;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.mapping.IntPair;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveSchemaTest {
    @Test
    public void exposesPublicFieldsConstraintsAndTableMacros() {
        EmployeeSchema target = new EmployeeSchema();
        ReflectiveSchema schema = new ReflectiveSchema(target);

        assertThat(schema.getTableNames())
            .containsExactlyInAnyOrder("EMPLOYEES", "DEPARTMENTS");

        Table employees = schema.getTable("EMPLOYEES");
        assertThat(employees).isInstanceOf(ScannableTable.class);
        assertThat(employees.getStatistic().getReferentialConstraints())
            .containsExactly(target.EMP_DEPT);
        assertRowsCanBeRead((ScannableTable) employees);

        assertThat(schema.getFunctionNames()).contains("employeeTable");
        Collection<Function> employeeTableFunctions = schema.getFunctions("employeeTable");
        assertThat(employeeTableFunctions).hasSize(1);
        Function function = employeeTableFunctions.iterator().next();
        assertThat(function).isInstanceOf(TableMacro.class);
        assertThat(((TableMacro) function).apply(Collections.emptyList()))
            .isSameAs(target.generatedEmployeeTable);
    }

    private static void assertRowsCanBeRead(ScannableTable table) {
        Enumerator<Object[]> enumerator = table.scan(null).enumerator();
        try {
            assertThat(enumerator.moveNext()).isTrue();
            assertThat(enumerator.current()).hasSize(2);
            assertThat(enumerator.moveNext()).isTrue();
            assertThat(enumerator.current()).hasSize(2);
            assertThat(enumerator.moveNext()).isFalse();
        } finally {
            enumerator.close();
        }
    }

    public static final class EmployeeSchema {
        public final Employee[] EMPLOYEES = {
            new Employee(1, "Ada"),
            new Employee(2, "Grace")
        };
        public final Department[] DEPARTMENTS = {
            new Department(10, "Engineering")
        };
        public final RelReferentialConstraint EMP_DEPT = RelReferentialConstraintImpl.of(
            Arrays.asList("hr", "EMPLOYEES"),
            Arrays.asList("hr", "DEPARTMENTS"),
            Collections.singletonList(IntPair.of(0, 0)));
        private final TranslatableTable generatedEmployeeTable = new SimpleTranslatableTable();

        public TranslatableTable employeeTable() {
            return generatedEmployeeTable;
        }
    }

    public static final class Employee {
        public final int id;
        public final String name;

        Employee(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class Department {
        public final int id;
        public final String name;

        Department(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static final class SimpleTranslatableTable extends AbstractTable
        implements TranslatableTable {
        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return typeFactory.builder()
                .add("VALUE", SqlTypeName.INTEGER)
                .build();
        }

        @Override
        public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable relOptTable) {
            throw new UnsupportedOperationException("The test only verifies table macro discovery");
        }
    }
}
