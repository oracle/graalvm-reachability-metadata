/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RegisteredBean;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ConstructorResolverTest {
    @Test
    void resolvesPublicAndDeclaredConstructorsAndConvertsArguments() {
        for (boolean publicOnly : new boolean[] {true, false}) {
            DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
            RootBeanDefinition definition = new RootBeanDefinition(Message.class);
            definition.setNonPublicAccessAllowed(!publicOnly);
            definition.getConstructorArgumentValues().addIndexedArgumentValue(0, "hello");
            factory.registerBeanDefinition("message", definition);
            assertEquals(Message.class, RegisteredBean.of(factory, "message")
                    .resolveConstructorOrFactoryMethod().getDeclaringClass());
            assertEquals("hello", factory.getBean("message", Message.class).text);
        }
    }

    @Test
    void autowiresEmptyArraysAndPreferredConstructors() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(Batch.class);
        definition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
        factory.registerBeanDefinition("batch", definition);
        assertEquals(0, factory.getBean("batch", Batch.class).messages.length);
        factory.registerSingleton("text", "preferred");
        assertEquals("preferred", factory.createBean(Message.class).text);
        assertEquals("preferred", factory.createBean(PrivateMessage.class).text);
    }

    @Test
    void resolvesPublicFactoryMethods() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(MessageFactory.class);
        definition.setFactoryMethodName("create");
        definition.setNonPublicAccessAllowed(false);
        definition.getConstructorArgumentValues().addIndexedArgumentValue(0, "factory");
        factory.registerBeanDefinition("message", definition);
        Message message = factory.getBean("message", Message.class);
        assertEquals("factory", message.text);
        assertSame(message, factory.getBean("message"));
    }

    public static class Message {
        private final String text;

        public Message(String text) {
            this.text = text;
        }
    }

    public static class PrivateMessage {
        private final String text;

        private PrivateMessage(String text) {
            this.text = text;
        }
    }

    public static class Batch {
        private final Message[] messages;

        public Batch(Message[] messages) {
            this.messages = messages;
        }
    }

    public static class MessageFactory {
        public static Message create(String text) {
            return new Message(text);
        }
    }
}
