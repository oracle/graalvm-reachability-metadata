/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.net.URL;

import org.apache.tomcat.util.res.StringManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TomcatStringManagerTest {

    private static final String BUNDLE_PACKAGE =
            "org_apache_tomcat_embed.tomcat_embed_core.tomcatcontextbundle";
    private static final String BUNDLE_RESOURCE =
            "org_apache_tomcat_embed/tomcat_embed_core/tomcatcontextbundle/LocalStrings.properties";
    private static final String TOMCAT_RESOURCE = "org/apache/tomcat/util/LocalStrings.properties";

    @Test
    void loadsTrustedApplicationMessagesFromTheContextClassLoader() {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        ClassLoader resourceClassLoader = new ResourceMappingClassLoader(originalClassLoader);

        try {
            thread.setContextClassLoader(resourceClassLoader);
            StringManager manager = StringManager.getManager(BUNDLE_PACKAGE);

            assertThat(manager.getString("diagnostics.threadDumpTitle")).isEqualTo("Full thread dump");
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }
    }

    @Test
    void representsPackageWithoutAMessageBundle() {
        StringManager manager = StringManager.getManager("example.without.messages");

        assertThat(manager.getLocale()).isNull();
        assertThat(manager.getString("missing-key")).isNull();
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
                return resourceLoader.getResource(TOMCAT_RESOURCE);
            }
            return null;
        }
    }
}
