/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.adapter.enumerable.EnumerableAggregate;
import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.adapter.enumerable.EnumerableRel;
import org.apache.calcite.adapter.enumerable.EnumerableRelImplementor;
import org.apache.calcite.adapter.enumerable.EnumerableValues;
import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.linq4j.tree.ClassDeclaration;
import org.apache.calcite.plan.ConventionTraitDef;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.RelCollations;
import org.apache.calcite.rel.core.AggregateCall;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rex.RexBuilder;
import org.apache.calcite.rex.RexLiteral;
import org.apache.calcite.schema.FunctionContext;
import org.apache.calcite.schema.impl.AggregateFunctionImpl;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.InferTypes;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.sql.validate.SqlUserDefinedAggFunction;
import org.apache.calcite.util.ImmutableBitSet;
import org.apache.calcite.util.Optionality;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class RexImpTableInnerUserDefinedAggReflectiveImplementorTest {
    @Test
    void generatesConstructorsForReflectiveAggregates() throws Exception {
        JavaTypeFactoryImpl typeFactory = new JavaTypeFactoryImpl();
        RexBuilder rexBuilder = new RexBuilder(typeFactory);
        VolcanoPlanner planner = new VolcanoPlanner();
        planner.addRelTraitDef(ConventionTraitDef.INSTANCE);
        RelOptCluster cluster = RelOptCluster.create(planner, rexBuilder);
        RelDataType integerType = typeFactory.createSqlType(SqlTypeName.INTEGER);
        RelDataType inputType = typeFactory.builder().add("v", integerType).build();
        RexLiteral one = rexBuilder.makeExactLiteral(BigDecimal.ONE, integerType);
        EnumerableValues input = EnumerableValues.create(
                cluster, inputType, ImmutableList.of(ImmutableList.of(one)));
        List<AggregateCall> calls = List.of(
                aggregateCall(input, integerType, "NO_ARG_SUM", NoArgSum.class),
                aggregateCall(input, integerType, "CONTEXT_SUM", ContextSum.class));
        EnumerableAggregate aggregate = new EnumerableAggregate(
                cluster,
                cluster.traitSetOf(EnumerableConvention.INSTANCE),
                input,
                ImmutableBitSet.of(),
                null,
                calls);
        EnumerableRelImplementor implementor =
                new EnumerableRelImplementor(rexBuilder, new HashMap<>());

        ClassDeclaration declaration =
                implementor.implementRoot(aggregate, EnumerableRel.Prefer.ARRAY);

        assertThat(declaration.toString())
                .contains("NoArgSum", "ContextSum", "FunctionContexts.of");
    }

    private static AggregateCall aggregateCall(
            EnumerableValues input, RelDataType resultType, String name, Class<?> functionClass) {
        AggregateFunctionImpl function =
                Objects.requireNonNull(AggregateFunctionImpl.create(functionClass));
        SqlUserDefinedAggFunction operator = new SqlUserDefinedAggFunction(
                new SqlIdentifier(name, SqlParserPos.ZERO),
                SqlKind.OTHER_FUNCTION,
                ReturnTypes.INTEGER,
                InferTypes.RETURN_TYPE,
                null,
                function,
                false,
                false,
                Optionality.FORBIDDEN);
        return AggregateCall.create(
                operator,
                false,
                false,
                false,
                List.of(),
                List.of(0),
                -1,
                null,
                RelCollations.EMPTY,
                resultType,
                name);
    }

    public static class NoArgSum {
        public NoArgSum() {
        }

        public Integer init() {
            return 0;
        }

        public Integer add(Integer accumulator, Integer value) {
            return accumulator + value;
        }

        public Integer result(Integer accumulator) {
            return accumulator;
        }
    }

    public static class ContextSum {
        public ContextSum(FunctionContext context) {
            Objects.requireNonNull(context);
        }

        public Integer init() {
            return 0;
        }

        public Integer add(Integer accumulator, Integer value) {
            return accumulator + value;
        }

        public Integer result(Integer accumulator) {
            return accumulator;
        }
    }
}
