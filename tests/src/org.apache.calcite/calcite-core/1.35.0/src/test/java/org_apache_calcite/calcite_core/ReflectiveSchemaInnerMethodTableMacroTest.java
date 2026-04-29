/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.List;
import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.Function;
import org.apache.calcite.schema.TableMacro;
import org.apache.calcite.schema.TranslatableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.junit.jupiter.api.Test;

public class ReflectiveSchemaInnerMethodTableMacroTest {
    @Test
    void applyInvokesSchemaMethodAndReturnsTranslatableTable() {
        ReportSchema target = new ReportSchema();
        ReflectiveSchema schema = new ReflectiveSchema(target);

        Collection<Function> functions = schema.getFunctions("salesReport");
        assertThat(functions).hasSize(1);

        TableMacro tableMacro = (TableMacro) functions.iterator().next();
        TranslatableTable table = tableMacro.apply(List.of("EMEA", 1_000));
        RelDataType rowType = table.getRowType(new JavaTypeFactoryImpl());

        assertThat(rowType.getFieldNames())
                .containsExactly("REGION", "MINIMUM_AMOUNT");
        assertThat(target.invocations).isEqualTo(1);
    }

    public static class ReportSchema {
        int invocations;

        public TranslatableTable salesReport(String region, Integer minimumAmount) {
            invocations++;
            return new ReportTable(region, minimumAmount);
        }
    }

    private static class ReportTable extends AbstractTable implements TranslatableTable {
        private final String region;
        private final Integer minimumAmount;

        ReportTable(String region, Integer minimumAmount) {
            this.region = region;
            this.minimumAmount = minimumAmount;
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            assertThat(region).isEqualTo("EMEA");
            assertThat(minimumAmount).isEqualTo(1_000);
            return typeFactory.builder()
                    .add("REGION", SqlTypeName.VARCHAR)
                    .add("MINIMUM_AMOUNT", SqlTypeName.INTEGER)
                    .build();
        }

        @Override
        public RelNode toRel(RelOptTable.ToRelContext context, RelOptTable relOptTable) {
            throw new UnsupportedOperationException("Method table macro invocation test only");
        }
    }
}
