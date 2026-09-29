/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.net.URL;

import org.apache.naming.StringManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NamingStringManagerTest {

    private static final String BUNDLE_PACKAGE =
            "org_apache_tomcat_embed.tomcat_embed_core.contextbundle";
    private static final String BUNDLE_RESOURCE =
            "org_apache_tomcat_embed/tomcat_embed_core/contextbundle/LocalStrings.properties";
    private static final String NAMING_RESOURCE = "org/apache/naming/LocalStrings.properties";

    @Test
    void representsPackageWithoutAMessageBundle() {
        StringManager manager = StringManager.getManager(NamingStringManagerTest.class);

        assertThat(manager.getString("missing-key")).isNull();
    }

    @Test
    void loadsTrustedApplicationMessagesFromTheContextClassLoader() {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        ClassLoader resourceClassLoader = new ResourceMappingClassLoader(originalClassLoader);

        try {
            thread.setContextClassLoader(resourceClassLoader);
            StringManager manager = StringManager.getManager(BUNDLE_PACKAGE);

            assertThat(manager.getString("namingContext.invalidName")).isEqualTo("Name is not valid");
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    private static final class ResourceMappingClassLoader extends ClassLoader {

        private final ClassLoader resourceLoader;

        private ResourceMappingClassLoader(ClassLoader resourceLoader) {
            super(null);
            this.resourceLoader = resourceLoader;
        }

        @Override
        public URL getResource(String name) {
            if (BUNDLE_RESOURCE.equals(name)) {
                return resourceLoader.getResource(NAMING_RESOURCE);
            }
            return null;
        }
    }
}
