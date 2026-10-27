/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.jndi.JNDIReferenceFactory;
import org.junit.jupiter.api.Test;

import javax.naming.Reference;

import static org.assertj.core.api.Assertions.assertThat;

public class JNDIReferenceFactoryTest {

    @Test
    void rebuildsAConnectionFactoryFromItsJndiReference() throws Exception {
        ActiveMQConnectionFactory original = new ActiveMQConnectionFactory("vm://jndi-broker?create=false");
        original.setClientID("jndi-client");
        Reference reference = original.getReference();

        ActiveMQConnectionFactory restored = (ActiveMQConnectionFactory) new JNDIReferenceFactory()
                .getObjectInstance(reference, null, null, null);

        assertThat(restored.getBrokerURL()).isEqualTo(original.getBrokerURL());
        assertThat(restored.getClientID()).isEqualTo("jndi-client");
    }

    @Test
    void loadsBootstrapTypesWithoutAnObjectClassLoader() throws Exception {
        Class<?> loaded = JNDIReferenceFactory.loadClass(new Object(), "java.util.Properties");

        assertThat(loaded).isEqualTo(java.util.Properties.class);
    }
}
