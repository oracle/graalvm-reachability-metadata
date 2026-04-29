/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.DataContext;
import org.apache.calcite.schema.FunctionContext;
import org.apache.calcite.schema.impl.AggregateFunctionImpl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

public class AggregateNodeInnerUdaAccumulatorFactoryTest {
    @Test
    public void createsUserDefinedAggregateByFunctionContextConstructor()
        throws Exception {
        AggregateFunctionImpl aggregateFunction = Objects.requireNonNull(
            AggregateFunctionImpl.create(ContextualSum.class),
            "ContextualSum must be recognized as an aggregate function");

        Object instance = createUdaAccumulatorInstance(aggregateFunction);

        assertThat(instance).isInstanceOf(ContextualSum.class);
        ContextualSum contextualSum = (ContextualSum) instance;
        int accumulated = contextualSum.add(contextualSum.init(), 1);
        assertThat(contextualSum.result(accumulated)).isEqualTo(2);
    }

    private static Object createUdaAccumulatorInstance(
        AggregateFunctionImpl aggregateFunction) throws Exception {
        Class<?> factoryClass = Class.forName(
            "org.apache.calcite.interpreter.AggregateNode$UdaAccumulatorFactory");
        Method createInstance = factoryClass.getDeclaredMethod(
            "createInstance", AggregateFunctionImpl.class, DataContext.class);
        createInstance.setAccessible(true);
        return createInstance.invoke(null, aggregateFunction, null);
    }

    public static final class ContextualSum {
        private final int contextParameterCount;

        public ContextualSum(FunctionContext context) {
            this.contextParameterCount = context.getParameterCount();
        }

        public int init() {
            return 0;
        }

        public int add(int accumulator, int value) {
            return accumulator + value + contextParameterCount;
        }

        public int result(int accumulator) {
            return accumulator;
        }
    }
}
