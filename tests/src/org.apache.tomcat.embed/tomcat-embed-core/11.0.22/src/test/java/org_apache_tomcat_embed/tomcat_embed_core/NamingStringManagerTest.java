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

    @Test
    void loadsBundleAvailableOnlyThroughContextClassLoader() {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        StringManager manager;
        try {
            thread.setContextClassLoader(
                    new ContextBundleClassLoader(NamingStringManagerTest.class.getClassLoader()));
            manager = StringManager.getManager("context.only.naming");
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }

        assertThat(manager.getString("contextBindings.noContextBoundToCL"))
                .isEqualTo("No naming context bound to this class loader");
    }

    private static final class ContextBundleClassLoader extends ClassLoader {

        private ContextBundleClassLoader(ClassLoader parent) {
            super(parent);
        }

        @Override
        public URL getResource(String name) {
            if ("context/only/naming/LocalStrings.properties".equals(name)) {
                return getParent().getResource("org/apache/naming/LocalStrings.properties");
            }
            return super.getResource(name);
        }
    }

    @Test
    void representsPackageWithoutAMessageBundle() {
        StringManager manager = StringManager.getManager(NamingStringManagerTest.class);

        assertThat(manager.getString("missing-key")).isNull();
    }
}
