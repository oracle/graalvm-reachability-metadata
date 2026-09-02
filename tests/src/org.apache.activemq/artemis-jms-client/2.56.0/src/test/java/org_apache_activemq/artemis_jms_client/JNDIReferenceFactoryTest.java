/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_jms_client;

import javax.naming.Reference;

import org.apache.activemq.artemis.jms.client.ActiveMQQueue;
import org.apache.activemq.artemis.jndi.JNDIReferenceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JNDIReferenceFactoryTest {

    @Test
    void recreatesQueueFromJndiReference() throws Exception {
        ActiveMQQueue queue = new ActiveMQQueue("orders.incoming");
        Reference reference = queue.getReference();

        Object recreated = new JNDIReferenceFactory().getObjectInstance(reference, null, null, null);

        assertThat(recreated).isInstanceOf(ActiveMQQueue.class);
        assertThat(((ActiveMQQueue) recreated).getQueueName()).isEqualTo("orders.incoming");
    }

    @Test
    void loadsClassWithBootstrapClassLoaderFallback() throws Exception {
        Class<?> loadedClass = JNDIReferenceFactory.loadClass("bootstrap-loader-anchor", String.class.getName());

        assertThat(loadedClass).isEqualTo(String.class);
    }
}
