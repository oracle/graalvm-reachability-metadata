/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.net.URL;

import org.apache.catalina.loader.ParallelWebappClassLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WebappClassLoaderBaseInnerPrivilegedJavaseGetResourceTest {

    @Test
    void javaRuntimeResourceLookupReturnsExistingResourceAndRejectsMissingResource() throws Exception {
        try (JavaseResourceClassLoader loader = new JavaseResourceClassLoader()) {
            URL objectClass = loader.getJavaseResource("java/lang/Object.class");

            assertThat(objectClass).isNotNull();
            assertThat(objectClass.getPath()).endsWith("java/lang/Object.class");
            assertThat(loader.getJavaseResource("java/lang/NoSuchRuntimeClass.class")).isNull();
        }
    }

    private static final class JavaseResourceClassLoader extends ParallelWebappClassLoader {

        private URL getJavaseResource(String name) {
            return new PrivilegedJavaseGetResource(name).run();
        }
    }
}
