/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class TableFunctionImplTest {
    @Test
    void appliesInstanceTableFunction() {
        TableFunction function = TableFunctionImpl.create(NumberTableFunction.class);
        JavaTypeFactoryImpl typeFactory = new JavaTypeFactoryImpl();

        assertThat(function).isNotNull();
        assertThat(function.getElementType(List.of(7))).isEqualTo(Object[].class);
        assertThat(function.getRowType(typeFactory, List.of(7)).getFieldNames()).containsExactly("VALUE");
    }

    public static class NumberTableFunction {
        public NumberTable eval(int value) {
            return new NumberTable(value);
        }
    }

    public static class NumberTable extends AbstractTable implements ScannableTable {
        private final int value;

        public NumberTable(int value) {
            this.value = value;
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return typeFactory.builder().add("VALUE", SqlTypeName.INTEGER).build();
        }

        @Override
        public Enumerable<Object[]> scan(DataContext root) {
            return Linq4j.asEnumerable(new Object[][] {{value}});
        }
    }
}
