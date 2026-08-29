/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_calcite.calcite_core;

import org.apache.calcite.rel.metadata.BuiltInMetadata;
import org.apache.calcite.rel.metadata.DefaultRelMetadataProvider;
import org.apache.calcite.rel.metadata.MetadataHandler;
import org.apache.calcite.rel.metadata.janino.RelMetadataHandlerGeneratorUtil;
import org.apache.calcite.rel.metadata.janino.RelMetadataHandlerGeneratorUtil.HandlerNameAndGeneratedCode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class DispatchGeneratorTest {
    @Test
    void generatesTypeDispatchForDefaultRowCountHandlers() {
        List<MetadataHandler<?>> handlers = DefaultRelMetadataProvider.INSTANCE
                .handlers(BuiltInMetadata.RowCount.Handler.class);

        HandlerNameAndGeneratedCode generated = RelMetadataHandlerGeneratorUtil.generateHandler(
                BuiltInMetadata.RowCount.Handler.class, handlers);

        assertThat(generated.getHandlerName()).endsWith("GeneratedMetadata_RowCountHandler");
        assertThat(generated.getGeneratedCode())
                .contains("instanceof org.apache.calcite.rel.core.Values");
    }
}
