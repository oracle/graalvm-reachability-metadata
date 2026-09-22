/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import org.apache.naming.StringManager;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class NamingStringManagerTest {

    @Test
    void retriesMissingBundleWithContextClassLoader() {
        Thread thread = Thread.currentThread();
        ClassLoader originalClassLoader = thread.getContextClassLoader();
        StringManager manager;
        try {
            thread.setContextClassLoader(NamingStringManagerTest.class.getClassLoader());
            manager = StringManager.getManager("missing.naming.messages");
        } finally {
            thread.setContextClassLoader(originalClassLoader);
        }

        assertThat(manager.getString("missing-key")).isNull();
    }

    @Test
    void representsPackageWithoutAMessageBundle() {
        StringManager manager = StringManager.getManager(NamingStringManagerTest.class);

        assertThat(manager.getString("missing-key")).isNull();
    }
}
