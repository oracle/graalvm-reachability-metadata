/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.jdbc.Driver;
import org.apache.calcite.runtime.Hook;
import org.apache.calcite.schema.FunctionContext;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.impl.AggregateFunctionImpl;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Objects;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

public class AggregateNodeInnerUdaAccumulatorFactoryTest {
    @Test
    public void bindableAggregateCreatesBuiltInUserDefinedAggregateByNoArgConstructor()
        throws Exception {
        try (Hook.Closeable ignored = Hook.ENABLE_BINDABLE.addThread(Hook.propertyJ(true));
             Connection connection = openConnection()) {
            addRowsSchema(connection);

            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                     "select sum(\"VALUE\") as \"TOTAL\" from \"ROWS\"")) {
                assertThat(resultSet.next()).isTrue();
                assertThat(resultSet.getInt("TOTAL")).isEqualTo(6);
                assertThat(resultSet.next()).isFalse();
            }
        }
    }

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

    private static Connection openConnection() throws Exception {
        return Objects.requireNonNull(
            new Driver().connect("jdbc:calcite:", new Properties()),
            "Calcite JDBC driver did not create a connection");
    }

    private static SchemaPlus addRowsSchema(Connection connection) throws Exception {
        CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
        SchemaPlus schema = calciteConnection.getRootSchema()
            .add("TEST_DATA", new ReflectiveSchema(new RowsSchema()));
        calciteConnection.setSchema("TEST_DATA");
        return schema;
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

    public static final class RowsSchema {
        public final InputRow[] ROWS = {
            new InputRow(1),
            new InputRow(2),
            new InputRow(3)
        };
    }

    public static final class InputRow {
        public final int VALUE;

        public InputRow(int value) {
            this.VALUE = value;
        }
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
