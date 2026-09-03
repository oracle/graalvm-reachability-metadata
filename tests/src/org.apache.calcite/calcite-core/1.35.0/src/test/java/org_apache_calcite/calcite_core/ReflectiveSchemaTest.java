/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.RelReferentialConstraint;
import org.apache.calcite.rel.RelReferentialConstraintImpl;
import org.apache.calcite.rel.logical.LogicalTableScan;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.util.mapping.IntPair;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveSchemaTest {
    @Test
    void discoversFunctionsAndReferentialConstraints() {
        ReflectiveSchema schema = new ReflectiveSchema(new Directory());

        assertThat(schema.getFunctions("view")).hasSize(1);
        assertThat(schema.getTable("people").getStatistic().getReferentialConstraints())
                .containsExactly(Directory.constraint);
    }

    public static class Directory {
        public static final RelReferentialConstraint constraint = RelReferentialConstraintImpl.of(
                List.of("people"), List.of("departments"), List.of(IntPair.of(0, 0)));
        public final Person[] people = {new Person(1)};
        public final Department[] departments = {new Department(1)};

        public LabelTable view() {
            return new LabelTable();
        }
    }

    public static class Person {
        public final int departmentId;

        public Person(int departmentId) {
            this.departmentId = departmentId;
        }
    }

    public static class Department {
        public final int id;

        public Department(int id) {
            this.id = id;
        }
    }

    public static class LabelTable extends AbstractTable implements TranslatableTable {
        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return typeFactory.builder().add("LABEL", SqlTypeName.VARCHAR).build();
        }

        @Override
        public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable relOptTable) {
            return LogicalTableScan.create(context.getCluster(), relOptTable, List.of());
        }
    }
}
