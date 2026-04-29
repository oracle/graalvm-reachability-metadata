/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Collections;
import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelReferentialConstraint;
import org.apache.calcite.rel.RelReferentialConstraintImpl;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.mapping.IntPair;
import org.junit.jupiter.api.Test;

public class ReflectiveSchemaTest {
    @Test
    void reflectiveSchemaExposesPublicFieldsConstraintsAndTableMacros() {
        SalesSchema target = new SalesSchema();
        ReflectiveSchema schema = new ReflectiveSchema(target);

        assertThat(schema.getTarget()).isSameAs(target);
        assertThat(schema.getTableNames()).containsExactlyInAnyOrder("EMPLOYEES", "DEPARTMENTS");

        Table employeesTable = schema.getTable("EMPLOYEES");
        assertThat(employeesTable).isNotNull();
        assertThat(employeesTable.getStatistic().getReferentialConstraints())
                .containsExactly(target.EMPLOYEES_DEPARTMENT);

        assertThat(schema.getFunctionNames()).contains("activeEmployees");
        Collection<?> activeEmployeeFunctions = schema.getFunctions("activeEmployees");
        assertThat(activeEmployeeFunctions).hasSize(1);
    }

    public static class SalesSchema {
        public final Employee[] EMPLOYEES = {
                new Employee(10, 1, "Ada"),
                new Employee(20, 2, "Grace")
        };
        public final Department[] DEPARTMENTS = {
                new Department(1, "Engineering"),
                new Department(2, "Research")
        };
        public final RelReferentialConstraint EMPLOYEES_DEPARTMENT =
                RelReferentialConstraintImpl.of(
                        Collections.singletonList("EMPLOYEES"),
                        Collections.singletonList("DEPARTMENTS"),
                        Collections.singletonList(IntPair.of(1, 0)));

        public TranslatableTable activeEmployees() {
            return new EmployeeTranslatableTable();
        }
    }

    public static class Employee {
        public final int id;
        public final int departmentId;
        public final String name;

        Employee(int id, int departmentId, String name) {
            this.id = id;
            this.departmentId = departmentId;
            this.name = name;
        }
    }

    public static class Department {
        public final int id;
        public final String name;

        Department(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    private static class EmployeeTranslatableTable extends AbstractTable
            implements TranslatableTable {
        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return typeFactory.builder()
                    .add("ID", SqlTypeName.INTEGER)
                    .add("DEPARTMENT_ID", SqlTypeName.INTEGER)
                    .add("NAME", SqlTypeName.VARCHAR)
                    .build();
        }

        @Override
        public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable relOptTable) {
            throw new UnsupportedOperationException("Function discovery test only");
        }
    }
}
