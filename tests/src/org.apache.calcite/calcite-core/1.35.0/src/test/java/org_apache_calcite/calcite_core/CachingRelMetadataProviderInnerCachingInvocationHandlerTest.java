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
import org.apache.calcite.rel.logical.LogicalValues;
import org.apache.calcite.rel.metadata.BuiltInMetadata;
import org.apache.calcite.rel.metadata.CachingRelMetadataProvider;
import org.apache.calcite.rel.metadata.DefaultRelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.metadata.UnboundMetadata;
import org.apache.calcite.rex.RexBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CachingRelMetadataProviderInnerCachingInvocationHandlerTest {
    @SuppressWarnings("deprecation")
    @Test
    void invokesAndCachesUnderlyingMetadata() {
        VolcanoPlanner planner = new VolcanoPlanner();
        RelOptCluster cluster = RelOptCluster.create(planner, new RexBuilder(new JavaTypeFactoryImpl()));
        LogicalValues values = LogicalValues.createOneRow(cluster);
        CachingRelMetadataProvider provider =
                new CachingRelMetadataProvider(DefaultRelMetadataProvider.INSTANCE, planner);
        UnboundMetadata<BuiltInMetadata.RowCount> unbound =
                provider.apply(LogicalValues.class, BuiltInMetadata.RowCount.class);
        BuiltInMetadata.RowCount metadata = unbound.bind(values, RelMetadataQuery.instance());

        assertThat(metadata.getRowCount()).isEqualTo(1D);
        assertThat(metadata.getRowCount()).isEqualTo(1D);
    }
}
