/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.io.InputStream;

import org.apache.catalina.loader.ParallelWebappClassLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WebappClassLoaderBaseTest {

    private static final String CLASS_RESOURCE =
            "org_apache_tomcat_embed/tomcat_embed_core/WebappClassLoaderBaseTest.class";

    @Test
    void delegatesClassAndResourceLookupsToItsParent() throws Exception {
        try (ParallelWebappClassLoader loader =
                new ParallelWebappClassLoader(WebappClassLoaderBaseTest.class.getClassLoader())) {
            loader.setDelegate(true);

            assertThat(loader.loadClass(getClass().getName())).isSameAs(getClass());
            assertThat(loader.getResource(CLASS_RESOURCE)).isNotNull();
            try (InputStream input = loader.getResourceAsStream(CLASS_RESOURCE)) {
                assertThat(input).isNotNull();
                assertThat(input.read()).isNotNegative();
            }
        }
    }
}
