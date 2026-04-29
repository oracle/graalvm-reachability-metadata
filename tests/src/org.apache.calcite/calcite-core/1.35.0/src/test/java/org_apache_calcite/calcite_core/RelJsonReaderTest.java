/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.RelOptPlanner;
import org.apache.calcite.plan.RelOptSchema;
import org.apache.calcite.plan.RelOptTable;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.externalize.RelJsonReader;
import org.apache.calcite.rel.externalize.RelJsonWriter;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.rex.RexBuilder;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RelJsonReaderTest {
    @Test
    public void readsSerializedLogicalValuesPlan() throws IOException {
        RelOptCluster cluster = cluster();
        LogicalValues original = LogicalValues.createOneRow(cluster);
        RelJsonWriter writer = new RelJsonWriter();
        original.explain(writer);

        RelJsonReader reader = new RelJsonReader(
            cluster,
            new EmptyRelOptSchema(cluster.getTypeFactory()),
            null);
        RelNode recreated = reader.read(writer.asString());

        assertThat(recreated).isInstanceOf(LogicalValues.class);
        LogicalValues values = (LogicalValues) recreated;
        assertThat(values.getRowType().getFieldNames()).containsExactly("ZERO");
        assertThat(values.getTuples()).hasSize(1);
    }

    private static RelOptCluster cluster() {
        VolcanoPlanner planner = new VolcanoPlanner();
        RexBuilder rexBuilder = new RexBuilder(new JavaTypeFactoryImpl());
        return RelOptCluster.create(planner, rexBuilder);
    }

    private static final class EmptyRelOptSchema implements RelOptSchema {
        private final RelDataTypeFactory typeFactory;

        private EmptyRelOptSchema(RelDataTypeFactory typeFactory) {
            this.typeFactory = typeFactory;
        }

        @Override
        public RelOptTable getTableForMember(List<String> names) {
            return null;
        }

        @Override
        public RelDataTypeFactory getTypeFactory() {
            return typeFactory;
        }

        @Override
        public void registerRules(RelOptPlanner planner) {
            // This schema does not expose tables, so it has no planner rules to register.
        }
    }
}
