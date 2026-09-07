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
    void reconstitutesDestinationFromReference() throws Exception {
        ActiveMQQueue queue = new ActiveMQQueue("invoices.pending");
        Reference reference = queue.getReference();

        Object restored = new JNDIReferenceFactory().getObjectInstance(reference, null, null, null);

        assertThat(restored).isInstanceOf(ActiveMQQueue.class);
        assertThat(((ActiveMQQueue) restored).getQueueName()).isEqualTo("invoices.pending");
    }

    @Test
    void usesAnchorLoaderForReferencedDestinationType() throws Exception {
        ActiveMQQueue queue = new ActiveMQQueue("invoices.completed");
        Reference reference = queue.getReference();

        Class<?> loadedType = JNDIReferenceFactory.loadClass(queue, reference.getClassName());

        assertThat(loadedType).isEqualTo(queue.getClass());
    }

    @Test
    void fallsBackToBootstrapLoader() throws Exception {
        Class<?> loadedType = JNDIReferenceFactory.loadClass("bootstrap-anchor", String.class.getName());

        assertThat(loadedType).isEqualTo(String.class);
    }
}
