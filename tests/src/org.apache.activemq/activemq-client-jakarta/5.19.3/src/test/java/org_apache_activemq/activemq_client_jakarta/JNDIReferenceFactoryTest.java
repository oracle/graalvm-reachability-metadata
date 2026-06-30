/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.activemq_client_jakarta;

import javax.naming.Reference;

import org.apache.activemq.command.ActiveMQQueue;
import org.apache.activemq.jndi.JNDIReferenceFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JNDIReferenceFactoryTest {

    @Test
    void recreatesStorableDestinationFromJndiReference() throws Exception {
        ActiveMQQueue queue = new ActiveMQQueue("orders.incoming");
        Reference reference = queue.getReference();

        Object recreated = new JNDIReferenceFactory().getObjectInstance(reference, null, null, null);

        assertThat(recreated).isInstanceOf(ActiveMQQueue.class);
        ActiveMQQueue recreatedQueue = (ActiveMQQueue) recreated;
        assertThat(recreatedQueue.getQueueName()).isEqualTo("orders.incoming");
    }

    @Test
    void loadsBootstrapClassWhenNoDefiningClassLoaderIsAvailable() throws Exception {
        Class<?> loadedClass = JNDIReferenceFactory.loadClass("bootstrap-loaded", "java.lang.String");

        assertThat(loadedClass).isEqualTo(String.class);
    }
}
