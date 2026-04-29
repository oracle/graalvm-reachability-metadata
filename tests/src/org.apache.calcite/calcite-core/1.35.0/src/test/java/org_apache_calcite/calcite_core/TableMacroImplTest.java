/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.TableMacro;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.schema.impl.TableMacroImpl;
import org.apache.calcite.sql.type.SqlTypeName;
import org.junit.jupiter.api.Test;

public class TableMacroImplTest {
    @Test
    void nonStaticTableMacroCreatesDeclaringClassAndInvokesEval() {
        TableMacro tableMacro = TableMacroImpl.create(DynamicTableMacro.class);

        assertThat(tableMacro).isNotNull();
        TranslatableTable table = tableMacro.apply(Collections.singletonList("REGIONS"));
        RelDataType rowType = table.getRowType(new JavaTypeFactoryImpl());

        assertThat(rowType.getFieldNames()).containsExactly("REGIONS");
    }

    public static class DynamicTableMacro {
        public DynamicTableMacro() {
        }

        public TranslatableTable eval(String columnName) {
            return new NamedColumnTable(columnName);
        }
    }

    private static class NamedColumnTable extends AbstractTable implements TranslatableTable {
        private final String columnName;

        NamedColumnTable(String columnName) {
            this.columnName = columnName;
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return typeFactory.builder()
                    .add(columnName, SqlTypeName.VARCHAR)
                    .build();
        }

        @Override
        public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable relOptTable) {
            throw new UnsupportedOperationException("Table macro invocation test only");
        }
    }
}
