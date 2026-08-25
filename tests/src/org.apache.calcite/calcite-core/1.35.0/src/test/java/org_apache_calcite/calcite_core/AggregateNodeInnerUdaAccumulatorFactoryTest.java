/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.DataContext;
import org.apache.calcite.DataContexts;
import org.apache.calcite.interpreter.Interpreter;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Linq4j;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.sql.type.SqlTypeName;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.RelBuilder;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AggregateNodeInnerUdaAccumulatorFactoryTest {
    @Test
    void interpretsBuiltInAggregateWithNoArgumentConstructor() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:calcite:")) {
            CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
            SchemaPlus rootSchema = calciteConnection.getRootSchema();
            rootSchema.add("numbers", new NumberTable());
            RelNode aggregate = createAggregate(rootSchema);
            DataContext dataContext = DataContexts.of(calciteConnection, rootSchema);

            try (Interpreter interpreter = new Interpreter(dataContext, aggregate)) {
                Object[] result = interpreter.toList().get(0);
                assertThat(result).containsExactly(6);
            }
        }
    }

    private RelNode createAggregate(SchemaPlus rootSchema) {
        FrameworkConfig config = Frameworks.newConfigBuilder().defaultSchema(rootSchema).build();
        RelBuilder builder = RelBuilder.create(config);
        return builder
                .scan("numbers")
                .aggregate(builder.groupKey(), builder.sum(false, "total", builder.field("value")))
                .build();
    }

    public static class NumberTable extends AbstractTable implements ScannableTable {
        @Override
        public Enumerable<Object[]> scan(DataContext root) {
            return Linq4j.asEnumerable(
                    List.<Object[]>of(new Object[] {1}, new Object[] {2}, new Object[] {3}));
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return typeFactory.builder().add("value", SqlTypeName.INTEGER).build();
        }
    }
}
