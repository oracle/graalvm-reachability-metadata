/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.DataContexts;
import org.apache.calcite.interpreter.Interpreter;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.RelBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class AggregateNodeInnerUdaAccumulatorFactoryTest {
    @Test
    void constructsAccumulatorForInterpretedSum() {
        RelBuilder builder = RelBuilder.create(Frameworks.newConfigBuilder().build());
        builder.values(new String[] {"v"}, 1, 2, 3);
        builder.aggregate(
                builder.groupKey(), builder.sum(false, "total", builder.field("v")));
        RelNode aggregate = builder.build();

        try (Interpreter interpreter = new Interpreter(DataContexts.EMPTY, aggregate)) {
            List<Object[]> rows = interpreter.toList();

            assertThat(rows).hasSize(1);
            assertThat(rows.get(0)).containsExactly(6);
        }
    }
}
