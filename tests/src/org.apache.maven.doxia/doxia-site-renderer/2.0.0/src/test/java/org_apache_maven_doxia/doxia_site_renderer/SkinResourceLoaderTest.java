/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven_doxia.doxia_site_renderer;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.apache.maven.doxia.siterenderer.DefaultSiteRenderer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SkinResourceLoaderTest {
    @Test
    void rendererResourceBundleIsAvailableFromClassLoader() throws Exception {
        ClassLoader classLoader = DefaultSiteRenderer.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream("site-renderer.properties")) {
            assertThat(stream).isNotNull();

            Properties properties = new Properties();
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));

            assertThat(properties)
                    .containsEntry("template.version", "Version")
                    .containsEntry("template.builtby", "Built by");
        }
    }
}
