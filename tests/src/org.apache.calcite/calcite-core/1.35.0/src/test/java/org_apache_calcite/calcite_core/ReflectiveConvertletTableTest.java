/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelRoot;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlBinaryOperator;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql2rel.ReflectiveConvertletTable;
import org.apache.calcite.sql2rel.SqlRexContext;
import org.apache.calcite.sql2rel.SqlRexConvertletTable;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.Planner;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveConvertletTableTest {
    @Test
    void invokesConvertletRegisteredByCallType() throws Exception {
        assertThat(convertPlan(new CallTypeConvertletTable()))
                .contains("LogicalValues(tuples=[[{ 1 }]])");
    }

    @Test
    void invokesConvertletRegisteredByOperatorType() throws Exception {
        assertThat(convertPlan(new OperatorTypeConvertletTable()))
                .contains("LogicalValues(tuples=[[{ 1 }]])");
    }

    private static String convertPlan(SqlRexConvertletTable convertletTable) throws Exception {
        try (Planner planner = Frameworks.getPlanner(Frameworks.newConfigBuilder()
                .defaultSchema(Frameworks.createRootSchema(true))
                .convertletTable(convertletTable)
                .build())) {
            SqlNode parsed = planner.parse("SELECT 1 + 2");
            SqlNode validated = planner.validate(parsed);
            RelRoot root = planner.rel(validated);
            return RelOptUtil.toString(root.rel);
        }
    }

    public static class CallTypeConvertletTable extends ReflectiveConvertletTable {
        public RexNode convertBasicCall(SqlRexContext context, SqlBasicCall call) {
            return context.convertExpression(call.operand(0));
        }
    }

    public static class OperatorTypeConvertletTable extends ReflectiveConvertletTable {
        public RexNode convertBinaryOperator(
                SqlRexContext context, SqlBinaryOperator operator, SqlCall call) {
            return context.convertExpression(call.operand(0));
        }
    }
}
