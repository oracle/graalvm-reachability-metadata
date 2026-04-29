/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import org.apache.calcite.DataContext;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.TableFunction;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.schema.impl.TableFunctionImpl;
import org.apache.calcite.sql.type.SqlTypeName;
import org.junit.jupiter.api.Test;

public class TableFunctionImplTest {
    @Test
    void nonStaticTableFunctionCreatesDeclaringClassAndInvokesEval() {
        TableFunction tableFunction = TableFunctionImpl.create(DynamicTableFunction.class);

        assertThat(tableFunction).isNotNull();
        RelDataType rowType = tableFunction.getRowType(
                new JavaTypeFactoryImpl(),
                Collections.singletonList("CALCITE_VALUE"));

        assertThat(rowType.getFieldNames()).containsExactly("CALCITE_VALUE");
        assertThat(tableFunction.getElementType(Collections.singletonList("OTHER_VALUE")))
                .isEqualTo(Object[].class);
    }

    public static class DynamicTableFunction {
        public DynamicTableFunction() {
        }

        public ScannableTable eval(String columnName) {
            return new SingleColumnTable(columnName);
        }
    }

    private static class SingleColumnTable extends AbstractTable implements ScannableTable {
        private final String columnName;

        SingleColumnTable(String columnName) {
            this.columnName = columnName;
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return typeFactory.builder()
                    .add(columnName, SqlTypeName.VARCHAR)
                    .build();
        }

        @Override
        public Enumerable<Object[]> scan(DataContext root) {
            return Linq4j.singletonEnumerable(new Object[] {columnName});
        }
    }
}
