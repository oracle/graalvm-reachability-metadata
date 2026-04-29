/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.enumerable.RexToLixTranslator;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.linq4j.tree.BlockBuilder;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexProgram;
import org.apache.calcite.schema.FunctionContext;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlConformanceEnum;
import org.apache.calcite.sql.validate.SqlUserDefinedFunction;
import org.junit.jupiter.api.Test;

public class RexToLixTranslatorTest {
    @Test
    void translatesInstanceUserDefinedFunctionWithFunctionContextConstructor() {
        JavaTypeFactoryImpl typeFactory = new JavaTypeFactoryImpl();
        RexBuilder rexBuilder = new RexBuilder(typeFactory);
        RelDataType integerType = typeFactory.createSqlType(SqlTypeName.INTEGER);
        RexNode literalArgument = rexBuilder.makeExactLiteral(
                BigDecimal.valueOf(7),
                integerType);
        SqlUserDefinedFunction function = scalarFunction(integerType);
        RexNode call = rexBuilder.makeCall(integerType, function, List.of(literalArgument));
        RexProgram program = RexProgram.create(
                typeFactory.builder().build(),
                List.of(call),
                null,
                typeFactory.builder().add("RESULT", integerType).build(),
                rexBuilder);
        BlockBuilder block = new BlockBuilder();

        List<Expression> expressions = RexToLixTranslator.translateProjects(
                program,
                typeFactory,
                SqlConformanceEnum.DEFAULT,
                block,
                null,
                null,
                Expressions.parameter(DataContext.class, "root"),
                (list, index, storageType) -> {
                    throw new AssertionError("No input fields are expected for this expression");
                },
                null);

        assertThat(expressions).hasSize(1);
        assertThat(block.toBlock().toString()).contains("FunctionContexts.of");
    }

    private static SqlUserDefinedFunction scalarFunction(RelDataType integerType) {
        ScalarFunction scalarFunction = Objects.requireNonNull(
                ScalarFunctionImpl.create(FunctionContextAwareFunction.class, "eval"),
                "scalar function");
        return new SqlUserDefinedFunction(
                new SqlIdentifier("CONTEXT_AWARE", SqlParserPos.ZERO),
                SqlKind.OTHER_FUNCTION,
                ReturnTypes.explicit(integerType),
                null,
                null,
                scalarFunction);
    }

    public static class FunctionContextAwareFunction {
        private final FunctionContext context;

        public FunctionContextAwareFunction(FunctionContext context) {
            this.context = context;
        }

        public int eval(int value) {
            return value + context.getParameterCount();
        }
    }
}
