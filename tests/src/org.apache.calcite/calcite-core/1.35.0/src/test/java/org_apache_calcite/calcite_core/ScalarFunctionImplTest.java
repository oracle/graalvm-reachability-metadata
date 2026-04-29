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
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.TableFunction;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.apache.calcite.sql.type.SqlTypeName;

import com.google.common.collect.ImmutableMultimap;

import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ScalarFunctionImplTest {
    @Test
    @SuppressWarnings("deprecation")
    public void createAllDiscoversStaticAndInstanceScalarMethods() {
        ImmutableMultimap<String, ScalarFunction> functions = ScalarFunctionImpl.createAll(ScalarLibrary.class);

        assertThat(functions.get("greeting")).hasSize(1);
        assertThat(functions.get("increment")).hasSize(1);
        assertThat(functions.get("wait")).isEmpty();
        assertThat(functions.get("greeting").iterator().next().getParameters()).hasSize(1);
        assertThat(functions.get("increment").iterator().next().getParameters()).hasSize(1);
    }

    @Test
    public void functionsDiscoversScalarAndTableFunctions() {
        ImmutableMultimap<String, Function> functions = ScalarFunctionImpl.functions(MixedFunctionLibrary.class);

        assertThat(functions.get("doubleValue")).hasSize(1);
        assertThat(functions.get("rowsFor")).hasSize(1);
        assertThat(functions.get("doubleValue").iterator().next()).isInstanceOf(ScalarFunction.class);
        assertThat(functions.get("rowsFor").iterator().next()).isInstanceOf(TableFunction.class);
    }

    public static final class ScalarLibrary {
        public ScalarLibrary() {
        }

        public static String greeting(String name) {
            return "hello " + name;
        }

        public int increment(int value) {
            return value + 1;
        }
    }

    public static final class MixedFunctionLibrary {
        private MixedFunctionLibrary() {
        }

        public static int doubleValue(int value) {
            return value * 2;
        }

        public static ScannableTable rowsFor(String label) {
            return new SingleColumnTable(label);
        }
    }

    private static final class SingleColumnTable extends AbstractTable implements ScannableTable {
        private final String label;

        private SingleColumnTable(String label) {
            this.label = label;
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return typeFactory.builder()
                .add("LABEL", SqlTypeName.VARCHAR)
                .build();
        }

        @Override
        public Enumerable<Object[]> scan(DataContext root) {
            return Linq4j.asEnumerable(Collections.singletonList(new Object[] {label}));
        }
    }
}
