/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalTableScan;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.TableMacro;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.schema.impl.TableMacroImpl;
import org.apache.calcite.sql.type.SqlTypeName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TableMacroImplTest {
    @Test
    void appliesInstanceTableMacro() {
        TableMacro macro = TableMacroImpl.create(LabelTableMacro.class);

        assertThat(macro).isNotNull();
        assertThat(macro.apply(List.of("active")))
                .isInstanceOf(LabelTable.class)
                .extracting(table -> ((LabelTable) table).label)
                .isEqualTo("active");
    }

    public static class LabelTableMacro {
        public LabelTable eval(String label) {
            return new LabelTable(label);
        }
    }

    public static class LabelTable extends AbstractTable implements TranslatableTable {
        private final String label;

        public LabelTable(String label) {
            this.label = label;
        }

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
