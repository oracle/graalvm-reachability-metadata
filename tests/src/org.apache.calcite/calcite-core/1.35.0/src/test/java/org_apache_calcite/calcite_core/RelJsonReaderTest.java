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
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.util.JsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class RelJsonReaderTest {
    @Test
    void reconstructsLogicalValuesFromJson() throws Exception {
        JavaTypeFactoryImpl typeFactory = new JavaTypeFactoryImpl();
        RelOptCluster cluster = RelOptCluster.create(new VolcanoPlanner(), new RexBuilder(typeFactory));
        LogicalValues values = LogicalValues.createOneRow(cluster);
        RelJsonWriter writer = new RelJsonWriter(new JsonBuilder());
        values.explain(writer);
        SchemaPlus rootSchema = Frameworks.createRootSchema(true);
        RelJsonReader reader = new RelJsonReader(cluster, new EmptyRelOptSchema(typeFactory), rootSchema);

        RelNode restored = reader.read(writer.asString());

        assertThat(restored).isInstanceOf(LogicalValues.class);
        assertThat(((LogicalValues) restored).getTuples()).hasSize(1);
    }

    private static class EmptyRelOptSchema implements RelOptSchema {
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
        }
    }
}
