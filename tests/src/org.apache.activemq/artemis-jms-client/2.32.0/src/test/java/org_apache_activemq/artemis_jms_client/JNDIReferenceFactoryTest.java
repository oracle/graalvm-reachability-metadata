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
    void restoresQueueConfigurationFromJndiReference() throws Exception {
        ActiveMQQueue original = new ActiveMQQueue("orders.awaiting-fulfillment");
        Reference reference = original.getReference();

        Object restored = new JNDIReferenceFactory().getObjectInstance(reference, null, null, null);

        assertThat(restored).isInstanceOf(ActiveMQQueue.class);
        assertThat(((ActiveMQQueue) restored).getQueueName()).isEqualTo(original.getQueueName());
    }

    @Test
    void loadsReferencedQueueTypeWithBootstrapLoaderFallback() throws Exception {
        ActiveMQQueue queue = new ActiveMQQueue("orders.completed");
        Reference reference = queue.getReference();

        Class<?> loadedType = JNDIReferenceFactory.loadClass("bootstrap-loader-anchor", reference.getClassName());

        assertThat(loadedType).isEqualTo(queue.getClass());
    }
}
