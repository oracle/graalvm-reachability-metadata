/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import javax.naming.StringRefAddr;

import org.apache.naming.ResourceRef;
import org.apache.naming.factory.ResourceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FactoryBaseTest {

    @Test
    void createsConfiguredFactoryWithContextClassLoader() throws Exception {
        Object resource = new ResourceFactory().getObjectInstance(reference(), null, null, null);

        assertThat(resource).isEqualTo("created javax.sql.DataSource");
    }

    @Test
    void createsConfiguredFactoryWithoutContextClassLoader() throws Exception {
        Thread thread = Thread.currentThread();
        ClassLoader originalLoader = thread.getContextClassLoader();
        thread.setContextClassLoader(null);
        try {
            Object resource = new ResourceFactory().getObjectInstance(reference(), null, null, null);
            assertThat(resource).isEqualTo("created javax.sql.DataSource");
        } finally {
            thread.setContextClassLoader(originalLoader);
        }
    }

    private static ResourceRef reference() {
        ResourceRef reference = new ResourceRef("javax.sql.DataSource", null, null, null, true);
        reference.add(new StringRefAddr("factory", ResourceFactoryTest.class.getName()));
        return reference;
    }
}
