/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Collections;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexRangeRef;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlLiteral;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.SqlOperator;
import org.apache.calcite.sql.SqlSpecialOperator;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.validate.SqlValidator;
import org.apache.calcite.sql2rel.InitializerExpressionFactory;
import org.apache.calcite.sql2rel.ReflectiveConvertletTable;
import org.apache.calcite.sql2rel.SqlRexContext;
import org.apache.calcite.sql2rel.SqlRexConvertlet;
import org.junit.jupiter.api.Test;

public class ReflectiveConvertletTableTest {
    private final TestRexContext context = new TestRexContext();

    @Test
    void nodeTypeConvertletInvokesRegisteredPublicConversionMethod() {
        RexNode expectedNode = context.literal(11);
        NodeTypeConvertletTable table = new NodeTypeConvertletTable(expectedNode);
        SqlBasicCall call = call(new CoverageOperator("NODE_CONVERTLET"));

        SqlRexConvertlet convertlet = table.get(call);
        assertThat(convertlet).isNotNull();

        RexNode convertedNode = convertlet.convertCall(context, call);

        assertThat(convertedNode).isSameAs(expectedNode);
        assertThat(table.invocations).isEqualTo(1);
        assertThat(table.lastContext).isSameAs(context);
        assertThat(table.lastCall).isSameAs(call);
    }

    @Test
    void operatorTypeConvertletInvokesRegisteredPublicConversionMethod() {
        RexNode expectedNode = context.literal(29);
        OperatorTypeConvertletTable table = new OperatorTypeConvertletTable(expectedNode);
        CoverageOperator operator = new CoverageOperator("OPERATOR_CONVERTLET");
        SqlBasicCall call = call(operator);

        SqlRexConvertlet convertlet = table.get(call);
        assertThat(convertlet).isNotNull();

        RexNode convertedNode = convertlet.convertCall(context, call);

        assertThat(convertedNode).isSameAs(expectedNode);
        assertThat(table.invocations).isEqualTo(1);
        assertThat(table.lastContext).isSameAs(context);
        assertThat(table.lastOperator).isSameAs(operator);
        assertThat(table.lastCall).isSameAs(call);
    }

    private static SqlBasicCall call(SqlOperator operator) {
        return new SqlBasicCall(operator, Collections.emptyList(), SqlParserPos.ZERO);
    }

    public static class NodeTypeConvertletTable extends ReflectiveConvertletTable {
        private final RexNode result;
        private int invocations;
        private SqlRexContext lastContext;
        private SqlCall lastCall;

        NodeTypeConvertletTable(RexNode result) {
            this.result = result;
        }

        public RexNode convertSqlBasicCall(SqlRexContext context, SqlBasicCall call) {
            invocations++;
            lastContext = context;
            lastCall = call;
            return result;
        }
    }

    public static class OperatorTypeConvertletTable extends ReflectiveConvertletTable {
        private final RexNode result;
        private int invocations;
        private SqlRexContext lastContext;
        private CoverageOperator lastOperator;
        private SqlCall lastCall;

        OperatorTypeConvertletTable(RexNode result) {
            this.result = result;
        }

        public RexNode convertCoverageOperator(SqlRexContext context, CoverageOperator operator, SqlCall call) {
            invocations++;
            lastContext = context;
            lastOperator = operator;
            lastCall = call;
            return result;
        }
    }

    public static class CoverageOperator extends SqlSpecialOperator {
        CoverageOperator(String name) {
            super(name, SqlKind.OTHER_FUNCTION);
        }
    }

    private static class TestRexContext implements SqlRexContext {
        private final JavaTypeFactoryImpl typeFactory = new JavaTypeFactoryImpl();
        private final RexBuilder rexBuilder = new RexBuilder(typeFactory);

        RexNode literal(int value) {
            return rexBuilder.makeExactLiteral(BigDecimal.valueOf(value));
        }

        @Override
        public RexNode convertExpression(SqlNode expression) {
            throw new UnsupportedOperationException("convertExpression");
        }

        @Override
        public int getGroupCount() {
            return -1;
        }

        @Override
        public RexBuilder getRexBuilder() {
            return rexBuilder;
        }

        @Override
        public RexRangeRef getSubQueryExpr(SqlCall call) {
            throw new UnsupportedOperationException("subQueryExpr");
        }

        @Override
        public RelDataTypeFactory getTypeFactory() {
            return typeFactory;
        }

        @Override
        public InitializerExpressionFactory getInitializerExpressionFactory() {
            throw new UnsupportedOperationException("initializerExpressionFactory");
        }

        @Override
        public SqlValidator getValidator() {
            throw new UnsupportedOperationException("validator");
        }

        @Override
        public RexNode convertLiteral(SqlLiteral literal) {
            throw new UnsupportedOperationException("literal");
        }
    }
}
