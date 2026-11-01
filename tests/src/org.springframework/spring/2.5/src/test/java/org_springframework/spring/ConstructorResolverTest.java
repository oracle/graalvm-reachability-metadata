/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.beans.factory.support.SimpleInstantiationStrategy;

import static org.junit.jupiter.api.Assertions.*;

public class ConstructorResolverTest {
    public static class Message {
        private final String text;
        private final int copies;

        public Message(String text, int copies) {
            this.text = text;
            this.copies = copies;
        }
    }

    @Test
    void resolvesAndConvertsConfiguredConstructorArguments() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.setInstantiationStrategy(new SimpleInstantiationStrategy());
        RootBeanDefinition definition = new RootBeanDefinition(Message.class);
        definition.getConstructorArgumentValues().addIndexedArgumentValue(0, "payload");
        definition.getConstructorArgumentValues().addIndexedArgumentValue(1, "3");
        factory.registerBeanDefinition("message", definition);
        try {
            Message result = (Message) factory.getBean("message");
            assertEquals("payload", result.text);
            assertEquals(3, result.copies);
        } finally {
            factory.destroySingletons();
        }
    }
}
