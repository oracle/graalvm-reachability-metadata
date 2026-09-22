/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.catalina.LifecycleState;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.loader.ParallelWebappClassLoader;
import org.apache.catalina.loader.WebappLoader;
import org.apache.catalina.webresources.StandardRoot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class WebappLoaderTest {

    @TempDir
    private Path temporaryDirectory;

    @Test
    void startsConfiguredWebApplicationClassLoader() throws Exception {
        Files.createDirectories(temporaryDirectory.resolve("WEB-INF/classes"));
        Files.createDirectories(temporaryDirectory.resolve("WEB-INF/lib"));

        StandardContext context = new StandardContext();
        context.setName("loader-test");
        context.setPath("/loader-test");
        context.setDocBase(temporaryDirectory.toString());
        StandardHost host = new StandardHost();
        host.setName("localhost");
        context.setParent(host);

        StandardRoot resources = new StandardRoot(context);
        resources.start();
        context.setResources(resources);

        WebappLoader loader = new WebappLoader();
        loader.setContext(context);
        loader.setLoaderClass(ConfiguredWebappClassLoader.class.getName());
        try {
            loader.start();

            assertThat(loader.getClassLoader()).isInstanceOf(ConfiguredWebappClassLoader.class);
            assertThat(loader.getClassLoader().getResource("server-embed.xml")).isNotNull();
        } finally {
            if (loader.getState().isAvailable()) {
                loader.stop();
            }
            if (loader.getState() != LifecycleState.DESTROYED) {
                loader.destroy();
            }
            if (resources.getState().isAvailable()) {
                resources.stop();
            }
            if (resources.getState() != LifecycleState.DESTROYED) {
                resources.destroy();
            }
        }
    }

    public static class ConfiguredWebappClassLoader extends ParallelWebappClassLoader {
        public ConfiguredWebappClassLoader(ClassLoader parent) {
            super(parent);
        }
    }
}
