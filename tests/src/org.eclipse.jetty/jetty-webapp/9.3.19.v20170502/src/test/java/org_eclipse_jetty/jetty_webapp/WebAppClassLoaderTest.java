/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_webapp;

import java.io.IOException;
import java.net.URL;
import java.security.PermissionCollection;
import java.util.Collections;
import java.util.List;

import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppClassLoader;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.assertThat;

public class WebAppClassLoaderTest {
    private static final String JETTY_WEB_DEFAULT_XML = "org/eclipse/jetty/webapp/webdefault.xml";

    @Test
    @Timeout(60)
    void findsParentResourceWhenParentLoaderHasPriority() throws Exception {
        WebAppClassLoader classLoader = new WebAppClassLoader(
                WebAppClassLoader.class.getClassLoader(), new LoaderContext(true));

        try {
            URL resource = classLoader.getResource(JETTY_WEB_DEFAULT_XML);

            assertThat(resource).isNotNull();
            assertThat(resource.toString()).contains("webdefault.xml");
        } finally {
            classLoader.close();
        }
    }

    @Test
    @Timeout(60)
    void fallsBackToParentResourceAfterWebAppLookup() throws Exception {
        WebAppClassLoader classLoader = new WebAppClassLoader(
                WebAppClassLoader.class.getClassLoader(), new LoaderContext(false));

        try {
            URL resource = classLoader.getResource(JETTY_WEB_DEFAULT_XML);

            assertThat(resource).isNotNull();
            assertThat(resource.toString()).contains("webdefault.xml");
        } finally {
            classLoader.close();
        }
    }

    @Test
    @Timeout(60)
    void combinesParentResourcesWithWebAppResources() throws Exception {
        WebAppClassLoader classLoader = new WebAppClassLoader(
                WebAppClassLoader.class.getClassLoader(), new LoaderContext(false));

        try {
            List<URL> resources = Collections.list(classLoader.getResources(JETTY_WEB_DEFAULT_XML));

            assertThat(resources).isNotEmpty();
            assertThat(resources).anySatisfy(resource -> assertThat(resource.toString()).contains("webdefault.xml"));
        } finally {
            classLoader.close();
        }
    }

    @Test
    @Timeout(60)
    void fallsBackToParentClassLoaderForClassesOutsideTheWebApp() throws Exception {
        WebAppClassLoader classLoader = new WebAppClassLoader(
                WebAppClassLoader.class.getClassLoader(), new LoaderContext(false));

        try {
            Class<?> loadedClass = classLoader.loadClass(WebAppContext.class.getName());

            assertThat(loadedClass).isSameAs(WebAppContext.class);
        } finally {
            classLoader.close();
        }
    }

    private static final class LoaderContext implements WebAppClassLoader.Context {
        private final boolean parentLoaderPriority;

        private LoaderContext(boolean parentLoaderPriority) {
            this.parentLoaderPriority = parentLoaderPriority;
        }

        @Override
        public Resource newResource(String urlOrPath) throws IOException {
            return Resource.newResource(urlOrPath);
        }

        @Override
        public PermissionCollection getPermissions() {
            return null;
        }

        @Override
        public boolean isSystemClass(String clazz) {
            return false;
        }

        @Override
        public boolean isServerClass(String clazz) {
            return false;
        }

        @Override
        public boolean isParentLoaderPriority() {
            return parentLoaderPriority;
        }

        @Override
        public String getExtraClasspath() {
            return null;
        }
    }
}
