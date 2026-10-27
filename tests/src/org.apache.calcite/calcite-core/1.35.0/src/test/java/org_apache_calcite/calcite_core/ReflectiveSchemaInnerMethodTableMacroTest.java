/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalTableScan;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.TableMacro;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveSchemaInnerMethodTableMacroTest {
    @Test
    void invokesMethodBackedTableMacro() {
        ReflectiveSchema schema = new ReflectiveSchema(new Directory());
        TableMacro macro = (TableMacro) schema.getFunctions("table").iterator().next();

        NamedTable table = (NamedTable) macro.apply(List.of("current"));

        assertThat(table.name).isEqualTo("current");
    }

    public static class Directory {
        public NamedTable table(String name) {
            return new NamedTable(name);
        }
    }

    public static class NamedTable extends AbstractTable implements TranslatableTable {
        private final String name;

        public NamedTable(String name) {
            this.name = name;
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return typeFactory.builder().add("NAME", SqlTypeName.VARCHAR).build();
        }

        @Override
        public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable relOptTable) {
            return LogicalTableScan.create(context.getCluster(), relOptTable, List.of());
        }
    }
}
