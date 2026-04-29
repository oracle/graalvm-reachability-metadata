/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.jdbc.JavaTypeFactoryImpl;
import org.apache.calcite.plan.RelOptCluster;
import org.apache.calcite.plan.volcano.VolcanoPlanner;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rel.metadata.BuiltInMetadata;
import org.apache.calcite.rel.metadata.JaninoRelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMdRowCount;
import org.apache.calcite.rex.RexBuilder;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class JaninoRelMetadataProviderTest {
    @Test
    public void createsInitialMetadataHandlerProxy() {
        JaninoRelMetadataProvider provider = JaninoRelMetadataProvider.of(RelMdRowCount.SOURCE);
        BuiltInMetadata.RowCount.Handler handler = provider.handler(
            BuiltInMetadata.RowCount.Handler.class);
        RelNode rel = relNode();

        assertThat(handler).isInstanceOf(BuiltInMetadata.RowCount.Handler.class);
        assertThatExceptionOfType(JaninoRelMetadataProvider.NoHandler.class)
            .isThrownBy(() -> handler.getRowCount(rel, null))
            .satisfies(e -> assertThat(e.relClass).isEqualTo(rel.getClass()));
    }

    private static RelNode relNode() {
        VolcanoPlanner planner = new VolcanoPlanner();
        RexBuilder rexBuilder = new RexBuilder(new JavaTypeFactoryImpl());
        RelOptCluster cluster = RelOptCluster.create(planner, rexBuilder);
        return LogicalValues.createOneRow(cluster);
    }
}
