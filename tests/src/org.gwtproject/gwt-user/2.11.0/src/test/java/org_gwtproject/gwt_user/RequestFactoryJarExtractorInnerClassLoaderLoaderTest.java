/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_gwtproject.gwt_user;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.web.bindery.requestfactory.server.RequestFactoryJarExtractor.ClassLoaderLoader;

import org.junit.jupiter.api.Test;

import java.io.InputStream;

public class RequestFactoryJarExtractorInnerClassLoaderLoaderTest {
    @Test
    void delegatesResourceLookupToClassLoader() throws Exception {
        ClassLoaderLoader loader = new ClassLoaderLoader(
                RequestFactoryJarExtractorInnerClassLoaderLoaderTest.class.getClassLoader());
        String resourceName = RequestFactoryJarExtractorInnerClassLoaderLoaderTest.class.getName()
                .replace('.', '/') + ".class";

        assertThat(loader.exists(resourceName)).isTrue();
        try (InputStream stream = loader.getResourceAsStream(resourceName)) {
            assertThat(stream).isNotNull();
            assertThat(stream.read()).isNotEqualTo(-1);
        }
    }
}
