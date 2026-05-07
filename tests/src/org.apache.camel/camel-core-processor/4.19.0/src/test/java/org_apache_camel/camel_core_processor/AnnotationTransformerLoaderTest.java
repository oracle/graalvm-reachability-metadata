/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_core_processor;

import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.transformer.AnnotationTransformerLoader;
import org.apache.camel.spi.TransformerRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationTransformerLoaderTest {
    @Test
    void searchesClasspathForTransformerPackageServiceDescriptors() throws Exception {
        try (DefaultCamelContext context = new DefaultCamelContext()) {
            AnnotationTransformerLoader loader = new AnnotationTransformerLoader();
            loader.setCamelContext(context);
            TransformerRegistry registry = context.getTransformerRegistry();
            int registrySize = registry.size();

            loader.load(registry);

            assertThat(registry).hasSize(registrySize);
        }
    }
}
