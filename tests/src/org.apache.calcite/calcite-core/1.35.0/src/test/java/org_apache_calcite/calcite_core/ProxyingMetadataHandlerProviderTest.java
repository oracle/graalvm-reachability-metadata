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
import org.apache.calcite.rel.metadata.DefaultRelMetadataProvider;
import org.apache.calcite.rel.metadata.Metadata;
import org.apache.calcite.rel.metadata.MetadataDef;
import org.apache.calcite.rel.metadata.MetadataHandler;
import org.apache.calcite.rel.metadata.ProxyingMetadataHandlerProvider;
import org.apache.calcite.rel.metadata.RelMetadataProvider;
import org.apache.calcite.rel.metadata.RelMetadataQuery;
import org.apache.calcite.rel.metadata.UnboundMetadata;
import org.apache.calcite.rex.RexBuilder;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableMultimap;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

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

    @Test
    void reportsMissingMetadataHandlerForRelNode() {
        LogicalValues values = createOneRow();
        ProxyingMetadataHandlerProvider provider =
                new ProxyingMetadataHandlerProvider(new MissingMetadataProvider());
        BuiltInMetadata.RowCount.Handler handler =
                provider.handler(BuiltInMetadata.RowCount.Handler.class);

        try {
            handler.getRowCount(values, new RelMetadataQuery(provider));
            fail("Expected an exception for metadata without a handler");
        } catch (IllegalArgumentException exception) {
            assertThat(exception).hasMessageContaining("No handler for method");
        }
    }

    private LogicalValues createOneRow() {
        JavaTypeFactoryImpl typeFactory = new JavaTypeFactoryImpl();
        RelOptCluster cluster = RelOptCluster.create(new VolcanoPlanner(), new RexBuilder(typeFactory));
        return LogicalValues.createOneRow(cluster);
    }

    private static final class MissingMetadataProvider implements RelMetadataProvider {
        @Override
        public <M extends Metadata> UnboundMetadata<M> apply(
                Class<? extends RelNode> relClass, Class<? extends M> metadataClass) {
            return null;
        }

        @Override
        public <M extends Metadata> ImmutableMultimap<Method, MetadataHandler<M>> handlers(
                MetadataDef<M> def) {
            return ImmutableMultimap.of();
        }

        @Override
        public List<MetadataHandler<?>> handlers(
                Class<? extends MetadataHandler<?>> handlerClass) {
            return List.of();
        }
    }
}
