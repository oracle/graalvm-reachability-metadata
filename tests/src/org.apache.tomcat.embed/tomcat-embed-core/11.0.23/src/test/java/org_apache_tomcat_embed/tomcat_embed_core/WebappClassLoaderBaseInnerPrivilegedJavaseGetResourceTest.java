/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.loader.ParallelWebappClassLoader;
import org.apache.catalina.webresources.StandardRoot;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class WebappClassLoaderBaseInnerPrivilegedJavaseGetResourceTest {

    @Test
    void loadsJavaRuntimeClassesThroughTheWebappClassLoader() throws Exception {
        StandardContext context = new StandardContext();
        context.setName("webapp");
        StandardRoot resources = new StandardRoot(context);
        resources.start();
        try (ParallelWebappClassLoader loader = new ParallelWebappClassLoader(
                WebappClassLoaderBaseInnerPrivilegedJavaseGetResourceTest.class.getClassLoader())) {
            loader.setResources(resources);
            loader.start();
            assertThat(loader.loadClass("java.lang.Object")).isSameAs(Object.class);
        } finally {
            resources.stop();
            resources.destroy();
        }
    }
}
