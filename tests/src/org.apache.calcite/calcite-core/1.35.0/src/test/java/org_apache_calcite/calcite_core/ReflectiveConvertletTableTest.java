/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.sql.SqlBasicCall;
import org.apache.calcite.sql.SqlCall;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.SqlSpecialOperator;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql2rel.ReflectiveConvertletTable;
import org.apache.calcite.sql2rel.SqlRexContext;
import org.apache.calcite.sql2rel.SqlRexConvertlet;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectiveConvertletTableTest {
    @Test
    public void invokesConvertletRegisteredBySqlNodeType() {
        RecordingConvertletTable table = new RecordingConvertletTable();
        SqlCall call = SqlStdOperatorTable.PLUS.createCall(SqlParserPos.ZERO);

        SqlRexConvertlet convertlet = table.get(call);
        RexNode convertedNode = convertlet.convertCall(null, call);

        assertThat(convertedNode).isSameAs(table.nodeTypeResult());
        assertThat(table.nodeTypeCall()).isSameAs(call);
    }

    @Test
    public void invokesConvertletRegisteredBySqlOperatorType() {
        RecordingConvertletTable table = new RecordingConvertletTable();
        SqlSpecialOperator operator = new SqlSpecialOperator(
            "DYNAMIC_ACCESS_TEST",
            SqlKind.OTHER);
        SqlCall call = operator.createCall(SqlParserPos.ZERO);

        SqlRexConvertlet convertlet = table.get(call);
        RexNode convertedNode = convertlet.convertCall(null, call);

        assertThat(convertedNode).isSameAs(table.operatorTypeResult());
        assertThat(table.operatorTypeCall()).isSameAs(call);
        assertThat(table.operatorTypeOperator()).isSameAs(operator);
    }

    public static final class RecordingConvertletTable extends ReflectiveConvertletTable {
        private final RexBuilder rexBuilder = new RexBuilder(new JavaTypeFactoryImpl());
        private final RexNode nodeTypeResult = rexBuilder.makeLiteral(true);
        private final RexNode operatorTypeResult = rexBuilder.makeLiteral(false);
        private SqlBasicCall nodeTypeCall;
        private SqlSpecialOperator operatorTypeOperator;
        private SqlCall operatorTypeCall;

        public RexNode convertSqlBasicCall(SqlRexContext context, SqlBasicCall call) {
            nodeTypeCall = call;
            return nodeTypeResult;
        }

        public RexNode convertSqlSpecialOperator(
                SqlRexContext context,
                SqlSpecialOperator operator,
                SqlCall call) {
            operatorTypeOperator = operator;
            operatorTypeCall = call;
            return operatorTypeResult;
        }

        RexNode nodeTypeResult() {
            return nodeTypeResult;
        }

        RexNode operatorTypeResult() {
            return operatorTypeResult;
        }

        SqlBasicCall nodeTypeCall() {
            return nodeTypeCall;
        }

        SqlSpecialOperator operatorTypeOperator() {
            return operatorTypeOperator;
        }

        SqlCall operatorTypeCall() {
            return operatorTypeCall;
        }
    }
}
