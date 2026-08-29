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
import org.apache.calcite.rel.metadata.ChainedRelMetadataProvider;
import org.apache.calcite.rel.metadata.DefaultRelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.metadata.UnboundMetadata;
import org.apache.calcite.rex.RexBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ChainedRelMetadataProviderTest {
    @SuppressWarnings("deprecation")
    @Test
    void createsMetadataProxyForProviderChain() {
        RelOptCluster cluster = RelOptCluster.create(
                new VolcanoPlanner(), new RexBuilder(new JavaTypeFactoryImpl()));
        LogicalValues values = LogicalValues.createOneRow(cluster);
        RelMetadataProvider provider = ChainedRelMetadataProvider.of(List.of(
                DefaultRelMetadataProvider.INSTANCE,
                DefaultRelMetadataProvider.INSTANCE));
        UnboundMetadata<BuiltInMetadata.RowCount> unbound =
                provider.apply(LogicalValues.class, BuiltInMetadata.RowCount.class);

        BuiltInMetadata.RowCount metadata = unbound.bind(values, RelMetadataQuery.instance());

        assertThat(metadata.getRowCount()).isEqualTo(1D);
    }
}
