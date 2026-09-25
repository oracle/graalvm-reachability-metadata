/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.net.URL;
import java.util.Locale;
import java.util.Objects;

import org.apache.tomcat.util.res.StringManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TomcatStringManagerTest {

    private static final String CONTEXT_BUNDLE = "missing/tomcat/messages/LocalStrings.properties";

    @Test
    void loadsBundleFromContextClassLoaderFallback() {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        StringManager manager;
        try {
            thread.setContextClassLoader(new ContextResourceClassLoader());
            manager = StringManager.getManager("missing.tomcat.messages", Locale.ROOT);
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }

        assertThat(manager.getLocale()).isEqualTo(Locale.ENGLISH);
        assertThat(manager.getString("integration.logger.level")).isEqualTo("FINE");
    }

    @Test
    void representsPackageWithoutAMessageBundle() {
        StringManager manager = StringManager.getManager("example.without.messages");

        assertThat(manager.getLocale()).isNull();
        assertThat(manager.getString("missing-key")).isNull();
    }

    @Test
    void loadsAndFormatsTomcatMessages() {
        StringManager manager = StringManager.getManager("org.apache.tomcat.util");

        assertThat(manager.getString("diagnostics.setPropertyFail", "name", "expected", "actual"))
                .contains("name", "expected", "actual");
    }

    private static final class ContextResourceClassLoader extends ClassLoader {

        private final URL bundleUrl;

        private ContextResourceClassLoader() {
            super(null);
            bundleUrl = Objects.requireNonNull(TomcatStringManagerTest.class
                    .getResource("/classloader-logging.properties"));
        }

        @Override
        public URL getResource(String name) {
            if (CONTEXT_BUNDLE.equals(name)) {
                return bundleUrl;
            }
            return null;
        }
    }
}
