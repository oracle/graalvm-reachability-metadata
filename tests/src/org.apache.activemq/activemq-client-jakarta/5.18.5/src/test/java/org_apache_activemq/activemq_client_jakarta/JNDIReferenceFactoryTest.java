/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import static org.assertj.core.api.Assertions.assertThat;

import javax.naming.Context;
import javax.naming.Reference;
import javax.naming.StringRefAddr;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.jndi.JNDIReferenceFactory;
import org.junit.jupiter.api.Test;

public class JNDIReferenceFactoryTest {
    private static final String BROKER_URL = "vm://localhost?broker.persistent=false";

    @Test
    void objectFactoryRecreatesJndiStorableFromReference() throws Exception {
        Reference reference = new Reference(
                ActiveMQConnectionFactory.class.getName(), JNDIReferenceFactory.class.getName(), null);
        reference.add(new StringRefAddr(Context.PROVIDER_URL, BROKER_URL));

        Object object = new JNDIReferenceFactory().getObjectInstance(reference, null, null, null);

        assertThat(object).isInstanceOf(ActiveMQConnectionFactory.class);
        ActiveMQConnectionFactory connectionFactory = (ActiveMQConnectionFactory) object;
        assertThat(connectionFactory.getBrokerURL()).isEqualTo(BROKER_URL);
    }

    @Test
    void loadClassUsesDefaultClassLoaderWhenSourceObjectWasLoadedByBootstrap() throws Exception {
        Class<?> loadedClass = JNDIReferenceFactory.loadClass(new Object(), String.class.getName());

        assertThat(loadedClass).isEqualTo(String.class);
    }
}
