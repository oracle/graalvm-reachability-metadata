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
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.RelBuilder;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;

import static org.assertj.core.api.Assertions.assertThat;

public class AggregateNodeInnerUdaAccumulatorFactoryTest {
    @Test
    void interpretsBuiltInAggregateWithNoArgumentConstructor() throws Exception {
        RelBuilder builder = newRelBuilder();
        RelNode aggregate = builder
                .values(new String[] {"value"}, 1, 2, 3)
                .aggregate(builder.groupKey(), builder.sum(false, "total", builder.field("value")))
                .build();

        assertThat(execute(aggregate)).containsExactly(6);
    }

    private RelBuilder newRelBuilder() {
        FrameworkConfig config = Frameworks.newConfigBuilder().build();
        return RelBuilder.create(config);
    }

    private Object[] execute(RelNode aggregate) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:calcite:")) {
            CalciteConnection calciteConnection = connection.unwrap(CalciteConnection.class);
            DataContext dataContext =
                    DataContexts.of(calciteConnection, calciteConnection.getRootSchema());
            try (Interpreter interpreter = new Interpreter(dataContext, aggregate)) {
                return interpreter.toList().get(0);
            }
        }
    }
}
