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
import org.apache.calcite.rel.metadata.DefaultRelMetadataProvider;
import org.apache.calcite.rel.metadata.ProxyingMetadataHandlerProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rex.RexBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ProxyingMetadataHandlerProviderTest {
    @Test
    void suppliesMetadataThroughProxyHandler() {
        JavaTypeFactoryImpl typeFactory = new JavaTypeFactoryImpl();
        RelOptCluster cluster = RelOptCluster.create(new VolcanoPlanner(), new RexBuilder(typeFactory));
        LogicalValues values = LogicalValues.createOneRow(cluster);
        ProxyingMetadataHandlerProvider provider =
                new ProxyingMetadataHandlerProvider(DefaultRelMetadataProvider.INSTANCE);
        RelMetadataQuery metadataQuery = new RelMetadataQuery(provider);
        BuiltInMetadata.RowCount.Handler handler =
                provider.handler(BuiltInMetadata.RowCount.Handler.class);

        assertThat(handler.getRowCount(values, metadataQuery)).isEqualTo(1D);
    }
}
