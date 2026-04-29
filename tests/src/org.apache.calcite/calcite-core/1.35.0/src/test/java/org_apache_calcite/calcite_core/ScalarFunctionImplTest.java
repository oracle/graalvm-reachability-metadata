/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.common.collect.ImmutableMultimap;
import java.util.Collection;
import org.apache.calcite.DataContext;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.TableFunction;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.apache.calcite.sql.type.SqlTypeName;
import org.junit.jupiter.api.Test;

public class ScalarFunctionImplTest {
    @SuppressWarnings("deprecation")
    @Test
    void createAllDiscoversPublicStaticAndInstanceScalarMethods() {
        ImmutableMultimap<String, ScalarFunction> functions = ScalarFunctionImpl.createAll(ScalarFunctionCatalog.class);

        assertThat(functions.keySet()).containsExactlyInAnyOrder("increment", "surround");
        assertScalarFunction(functions.get("increment"), 1, SqlTypeName.INTEGER);
        assertScalarFunction(functions.get("surround"), 2, SqlTypeName.VARCHAR);
    }

    @Test
    void functionsDiscoversScalarAndTableFunctionsByMethodName() {
        ImmutableMultimap<String, Function> functions = ScalarFunctionImpl.functions(MixedFunctionCatalog.class);

        assertThat(functions.keySet()).containsExactlyInAnyOrder("upper", "singleColumnRows");
        assertThat(onlyFunction(functions.get("upper"))).isInstanceOf(ScalarFunction.class);
        assertThat(onlyFunction(functions.get("singleColumnRows"))).isInstanceOf(TableFunction.class);
    }

    private static void assertScalarFunction(Collection<ScalarFunction> functions, int parameterCount,
            SqlTypeName returnType) {
        ScalarFunction function = onlyFunction(functions);

        assertThat(function.getParameters()).hasSize(parameterCount);
        assertThat(function.getReturnType(new JavaTypeFactoryImpl()).getSqlTypeName()).isEqualTo(returnType);
    }

    private static <T extends Function> T onlyFunction(Collection<T> functions) {
        assertThat(functions).hasSize(1);
        return functions.iterator().next();
    }

    public static class ScalarFunctionCatalog {
        public ScalarFunctionCatalog() {
        }

        public static int increment(int value) {
            return value + 1;
        }

        public String surround(String prefix, String value) {
            return prefix + value + prefix;
        }
    }

    public static class MixedFunctionCatalog {
        private static final ScannableTable ROWS_TABLE = new SingleRowTable();

        public static String upper(String value) {
            return value.toUpperCase();
        }

        public static ScannableTable singleColumnRows() {
            return ROWS_TABLE;
        }
    }

    private static class SingleRowTable extends AbstractTable implements ScannableTable {
        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return typeFactory.builder()
                    .add("VALUE", SqlTypeName.VARCHAR)
                    .build();
        }

        @Override
        public Enumerable<Object[]> scan(DataContext root) {
            return Linq4j.singletonEnumerable(new Object[] {"value"});
        }
    }
}
