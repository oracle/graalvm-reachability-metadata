/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.DataContext;
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

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class TableFunctionImplTest {
    @Test
    public void instanceTableFunctionConstructsTargetAndInvokesEval() {
        InstanceTableFunction.reset();
        TableFunction function = TableFunctionImpl.create(InstanceTableFunction.class);

        assertThat(function).isNotNull();
        Type elementType = function.getElementType(Collections.singletonList("active"));

        assertThat(elementType).isEqualTo(Object[].class);
        assertThat(InstanceTableFunction.constructionCount()).isEqualTo(1);
        assertThat(InstanceTableFunction.lastFilter()).isEqualTo("active");
    }

    public static final class InstanceTableFunction {
        private static final AtomicInteger CONSTRUCTION_COUNT = new AtomicInteger();
        private static final AtomicReference<String> LAST_FILTER = new AtomicReference<>();

        public InstanceTableFunction() {
            CONSTRUCTION_COUNT.incrementAndGet();
        }

        public ScannableTable eval(String filter) {
            LAST_FILTER.set(filter);
            return new FilteredScannableTable(filter);
        }

        static void reset() {
            CONSTRUCTION_COUNT.set(0);
            LAST_FILTER.set(null);
        }

        static int constructionCount() {
            return CONSTRUCTION_COUNT.get();
        }

        static String lastFilter() {
            return LAST_FILTER.get();
        }
    }

    private static final class FilteredScannableTable extends AbstractTable implements ScannableTable {
        private final String filter;

        private FilteredScannableTable(String filter) {
            this.filter = filter;
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return typeFactory.builder()
                .add("FILTER", SqlTypeName.VARCHAR)
                .build();
        }

        @Override
        public Enumerable<Object[]> scan(DataContext root) {
            return Linq4j.asEnumerable(Collections.singletonList(new Object[] {filter}));
        }
    }
}
