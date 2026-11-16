/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.io.Serializable;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AutowireUtilsTest {
    @Test
    void injectsResolvableObjectFactoryAsLazyInterfaceDependency() {
        SequenceFactory sequence = new SequenceFactory();
        Client client = createClient(sequence);
        assertEquals(0, sequence.calls);
        assertEquals("value-1", client.service.read());
        assertEquals(1, sequence.calls);
    }

    static Client createClient(SequenceFactory sequence) {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerResolvableDependency(Service.class, sequence);
        RootBeanDefinition definition = new RootBeanDefinition(Client.class);
        definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
        factory.registerBeanDefinition("client", definition);
        return factory.getBean("client", Client.class);
    }

    public interface Service {
        String read();
    }

    public static class SequenceFactory implements ObjectFactory<Service>, Serializable {
        private static final long serialVersionUID = 1L;
        int calls;

        @Override
        public Service getObject() {
            String value = "value-" + ++this.calls;
            return () -> value;
        }
    }

    public static class Client {
        final Service service;

        public Client(Service service) {
            this.service = service;
        }
    }
}
