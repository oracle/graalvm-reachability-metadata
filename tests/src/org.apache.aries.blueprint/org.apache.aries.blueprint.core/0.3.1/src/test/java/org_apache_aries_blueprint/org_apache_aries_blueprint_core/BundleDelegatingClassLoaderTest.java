/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.aries.blueprint.container.GenericType;
import org.apache.aries.blueprint.utils.BundleDelegatingClassLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BundleDelegatingClassLoaderTest {
    private static final String SCHEMA_NAME = "blueprint-schemas/core.xsd";

    @Test
    void fallsBackToApplicationClassesWhenTheBundleCannotLoadThem() throws Exception {
        TestBundle bundle = new TestBundle(null);
        BundleDelegatingClassLoader loader = new BundleDelegatingClassLoader(
                bundle, BundleDelegatingClassLoaderTest.class.getClassLoader());

        assertThat(loader.getBundle()).isSameAs(bundle);
        assertThat(loader.loadClass("org.apache.aries.blueprint.container.GenericType"))
                .isSameAs(GenericType.class);
        GenericType type = GenericType.parse("java.util.List<java.lang.String>", loader);
        assertThat(type.getRawClass()).isEqualTo(List.class);
        assertThat(type.getActualTypeArgument(0).getRawClass()).isEqualTo(String.class);
    }

    @Test
    void readsSchemaFromFallbackWhenTheBundleHasNoResource() throws Exception {
        TestBundle bundle = new TestBundle(null) {
            @Override
            public URL getResource(String name) {
                return null;
            }
        };
        BundleDelegatingClassLoader loader = new BundleDelegatingClassLoader(bundle, new SchemaResources());

        URL schema = loader.getResource(SCHEMA_NAME);
        assertThat(schema).isNotNull();
        try (InputStream stream = schema.openStream()) {
            String content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).contains("<xsd:schema", "targetNamespace=\"http://www.osgi.org/xmlns/blueprint/v1.0.0\"");
        }
    }

    /// Maps a logical schema name to a packaged resource without defining or loading new classes.
    private static final class SchemaResources extends ClassLoader {
        private SchemaResources() {
            super(null);
        }

        @Override
        protected URL findResource(String name) {
            return SCHEMA_NAME.equals(name)
                    ? BundleDelegatingClassLoader.class.getResource("/org/apache/aries/blueprint/blueprint.xsd")
                    : null;
        }
    }
}
