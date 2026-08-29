/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.adapter.enumerable.EnumerableCalc;
import org.apache.calcite.adapter.enumerable.EnumerableRel;
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor;
import org.apache.calcite.adapter.enumerable.EnumerableValues;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.linq4j.tree.ClassDeclaration;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.rex.RexNode;
import org.apache.calcite.rex.RexProgram;
import org.apache.calcite.schema.FunctionContext;
import org.apache.calcite.schema.ScalarFunction;
import org.apache.calcite.schema.impl.ScalarFunctionImpl;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.InferTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlUserDefinedFunction;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class RexToLixTranslatorTest {
    @Test
    void generatesFunctionContextConstructorForScalarFunction() {
        JavaTypeFactoryImpl typeFactory = new JavaTypeFactoryImpl();
        RexBuilder rexBuilder = new RexBuilder(typeFactory);
        RelOptCluster cluster = RelOptCluster.create(new VolcanoPlanner(), rexBuilder);
        RelDataType integerType = typeFactory.createSqlType(SqlTypeName.INTEGER);
        RelDataType inputType = typeFactory.builder().add("v", integerType).build();
        RexLiteral one = rexBuilder.makeExactLiteral(BigDecimal.ONE, integerType);
        EnumerableValues input = EnumerableValues.create(
                cluster, inputType, ImmutableList.of(ImmutableList.of(one)));
        ScalarFunction function = Objects.requireNonNull(
                ScalarFunctionImpl.create(ContextIncrement.class, "eval"));
        SqlUserDefinedFunction operator = new SqlUserDefinedFunction(
                new SqlIdentifier("CONTEXT_INCREMENT", SqlParserPos.ZERO),
                SqlKind.OTHER_FUNCTION,
                ReturnTypes.INTEGER,
                InferTypes.RETURN_TYPE,
                null,
                function);
        RexNode call = rexBuilder.makeCall(
                integerType, operator, List.of(rexBuilder.makeInputRef(integerType, 0)));
        RelDataType outputType = typeFactory.builder().add("result", integerType).build();
        RexProgram program = RexProgram.create(inputType, List.of(call), null, outputType, rexBuilder);
        EnumerableCalc calc = EnumerableCalc.create(input, program);
        EnumerableRelImplementor implementor =
                new EnumerableRelImplementor(rexBuilder, new HashMap<>());

        ClassDeclaration declaration = implementor.implementRoot(calc, EnumerableRel.Prefer.ARRAY);

        assertThat(declaration.toString())
                .contains("ContextIncrement", "FunctionContexts.of");
    }

    public static class ContextIncrement {
        public ContextIncrement(FunctionContext context) {
            Objects.requireNonNull(context);
        }

        public Integer eval(Integer value) {
            return value + 1;
        }
    }
}
