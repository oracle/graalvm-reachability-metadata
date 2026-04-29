/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Objects;
import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.enumerable.EnumerableAggregate;
import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.adapter.enumerable.EnumerableRel;
import org.apache.calcite.adapter.enumerable.EnumerableRules;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.interpreter.Interpreters;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.rel.InvalidRelException;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.Aggregate;
import org.apache.calcite.schema.FunctionContext;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.impl.AggregateFunctionImpl;
import org.apache.calcite.sql.SqlIdentifier;
import org.apache.calcite.sql.SqlKind;
import org.apache.calcite.sql.parser.SqlParserPos;
import org.apache.calcite.sql.type.ReturnTypes;
import org.apache.calcite.sql.validate.SqlUserDefinedAggFunction;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.RelBuilder;
import org.apache.calcite.util.Optionality;
import org.junit.jupiter.api.Test;

public class RexImpTableInnerUserDefinedAggReflectiveImplementorTest {
    @Test
    void enumerableAggregateInstantiatesUserDefinedAggregatesReflectively()
            throws InvalidRelException {
        RelBuilder builder = newRelBuilder();
        NoArgSumAggregate.constructorCalls = 0;
        FunctionContextSumAggregate.lastParameterCount = -1;
        SqlUserDefinedAggFunction noArgSum = aggregateFunction(
                builder,
                "NO_ARG_SUM",
                NoArgSumAggregate.class);
        SqlUserDefinedAggFunction functionContextSum = aggregateFunction(
                builder,
                "FUNCTION_CONTEXT_SUM",
                FunctionContextSumAggregate.class);

        RelNode aggregate = builder
                .values(new String[] {"DEPARTMENT_ID", "SCORE"},
                        1, 10,
                        1, 20,
                        2, 7)
                .aggregate(builder.groupKey(builder.field("DEPARTMENT_ID")),
                        builder.aggregateCall(noArgSum, builder.field("SCORE"))
                                .as("NO_ARG_TOTAL"),
                        builder.aggregateCall(functionContextSum, builder.field("SCORE"))
                                .as("FUNCTION_CONTEXT_TOTAL"))
                .build();

        List<Object[]> rows = bindEnumerableAggregate(aggregate, builder);

        assertThat(rows)
                .extracting(row -> List.of(row[0], row[1], row[2]))
                .containsExactlyInAnyOrder(
                        List.of(1, 30, 30),
                        List.of(2, 7, 7));
        assertThat(NoArgSumAggregate.constructorCalls).isPositive();
        assertThat(FunctionContextSumAggregate.lastParameterCount).isEqualTo(1);
    }

    private static List<Object[]> bindEnumerableAggregate(
            RelNode aggregate,
            RelBuilder builder) throws InvalidRelException {
        Aggregate logicalAggregate = (Aggregate) aggregate;
        EnumerableRel enumerableInput = (EnumerableRel) Objects.requireNonNull(
                EnumerableRules.ENUMERABLE_VALUES_RULE.convert(logicalAggregate.getInput()),
                "enumerable input");
        EnumerableRel enumerableAggregate = new EnumerableAggregate(
                aggregate.getCluster(),
                aggregate.getCluster().traitSet().replace(EnumerableConvention.INSTANCE),
                enumerableInput,
                logicalAggregate.getGroupSet(),
                logicalAggregate.getGroupSets(),
                logicalAggregate.getAggCallList());
        RelNode bindableRel = Objects.requireNonNull(
                EnumerableRules.TO_BINDABLE.convert(enumerableAggregate),
                "bindable aggregate");
        return Interpreters.bindable(bindableRel)
                .bind(new TypeFactoryDataContext((JavaTypeFactory) builder.getTypeFactory()))
                .toList();
    }

    private static SqlUserDefinedAggFunction aggregateFunction(
            RelBuilder builder,
            String name,
            Class<?> aggregateClass) {
        AggregateFunctionImpl aggregateFunction = Objects.requireNonNull(
                AggregateFunctionImpl.create(aggregateClass),
                "aggregate function");
        return new SqlUserDefinedAggFunction(
                new SqlIdentifier(name, SqlParserPos.ZERO),
                SqlKind.OTHER_FUNCTION,
                ReturnTypes.explicit(builder.getTypeFactory().createJavaType(int.class)),
                null,
                null,
                aggregateFunction,
                false,
                false,
                Optionality.FORBIDDEN);
    }

    private static RelBuilder newRelBuilder() {
        FrameworkConfig config = Frameworks.newConfigBuilder()
                .defaultSchema(Frameworks.createRootSchema(true))
                .build();
        return RelBuilder.create(config);
    }

    public static class NoArgSumAggregate {
        static int constructorCalls;

        public NoArgSumAggregate() {
            constructorCalls++;
        }

        public int init() {
            return 0;
        }

        public int add(int accumulator, int value) {
            return accumulator + value;
        }

        public int result(int accumulator) {
            return accumulator;
        }
    }

    public static class FunctionContextSumAggregate {
        static int lastParameterCount = -1;

        public FunctionContextSumAggregate(FunctionContext context) {
            lastParameterCount = context.getParameterCount();
        }

        public int init() {
            return 0;
        }

        public int add(int accumulator, int value) {
            return accumulator + value;
        }

        public int result(int accumulator) {
            return accumulator;
        }
    }

    private static class TypeFactoryDataContext implements DataContext {
        private final JavaTypeFactory typeFactory;

        TypeFactoryDataContext(JavaTypeFactory typeFactory) {
            this.typeFactory = typeFactory;
        }

        @Override
        public SchemaPlus getRootSchema() {
            return null;
        }

        @Override
        public JavaTypeFactory getTypeFactory() {
            return typeFactory;
        }

        @Override
        public QueryProvider getQueryProvider() {
            return null;
        }

        @Override
        public Object get(String name) {
            return null;
        }
    }
}
