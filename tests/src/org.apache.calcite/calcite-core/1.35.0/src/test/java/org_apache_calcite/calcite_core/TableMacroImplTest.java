/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class TableMacroImplTest {
    @Test
    public void instanceTableMacroConstructsTargetAndInvokesEval() {
        InstanceTableMacro.reset();
        TableMacro macro = TableMacroImpl.create(InstanceTableMacro.class);

        assertThat(macro).isNotNull();
        TranslatableTable table = macro.apply(Arrays.asList("region", 2));

        assertThat(table).isInstanceOf(GeneratedTranslatableTable.class);
        GeneratedTranslatableTable generatedTable = (GeneratedTranslatableTable) table;
        assertThat(generatedTable.columnName()).isEqualTo("region");
        assertThat(generatedTable.multiplier()).isEqualTo(2);
        assertThat(InstanceTableMacro.constructionCount()).isEqualTo(1);
        assertThat(InstanceTableMacro.lastArguments()).containsExactly("region", 2);
    }

    public static final class InstanceTableMacro {
        private static final AtomicInteger CONSTRUCTION_COUNT = new AtomicInteger();
        private static final AtomicReference<List<Object>> LAST_ARGUMENTS = new AtomicReference<>();

        public InstanceTableMacro() {
            CONSTRUCTION_COUNT.incrementAndGet();
        }

        public TranslatableTable eval(String columnName, int multiplier) {
            LAST_ARGUMENTS.set(Arrays.asList(columnName, multiplier));
            return new GeneratedTranslatableTable(columnName, multiplier);
        }

        static void reset() {
            CONSTRUCTION_COUNT.set(0);
            LAST_ARGUMENTS.set(null);
        }

        static int constructionCount() {
            return CONSTRUCTION_COUNT.get();
        }

        static List<Object> lastArguments() {
            return LAST_ARGUMENTS.get();
        }
    }

    private static final class GeneratedTranslatableTable extends AbstractTable
        implements TranslatableTable {
        private final String columnName;
        private final int multiplier;

        private GeneratedTranslatableTable(String columnName, int multiplier) {
            this.columnName = columnName;
            this.multiplier = multiplier;
        }

        String columnName() {
            return columnName;
        }

        int multiplier() {
            return multiplier;
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return typeFactory.builder()
                .add(columnName, SqlTypeName.INTEGER)
                .build();
        }

        @Override
        public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable relOptTable) {
            throw new UnsupportedOperationException(
                "The test only verifies table macro application");
        }
    }
}
