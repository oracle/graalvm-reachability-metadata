/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.AutowiredAnnotationBeanPostProcessor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.QualifierAnnotationAutowireCandidateResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class Spring_beansTest {
    @Test
    void selectsQualifiedDependencyAndInjectsConfiguredValue() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.setAutowireCandidateResolver(new QualifierAnnotationAutowireCandidateResolver());
        factory.addEmbeddedValueResolver(value -> value.replace("${delivery.limit}", "16"));
        AutowiredAnnotationBeanPostProcessor processor = new AutowiredAnnotationBeanPostProcessor();
        processor.setBeanFactory(factory);
        factory.addBeanPostProcessor(processor);
        factory.registerBeanDefinition("fast", new RootBeanDefinition(FastChannel.class));
        factory.registerBeanDefinition("slow", new RootBeanDefinition(SlowChannel.class));
        factory.registerBeanDefinition("client", new RootBeanDefinition(Client.class));
        Client client = factory.getBean("client", Client.class);
        assertEquals("express", client.channel.deliver());
        assertEquals(16, client.limit);
    }

    public interface Channel {
        String deliver();
    }

    @Qualifier("primary")
    public static class FastChannel implements Channel {
        @Override
        public String deliver() {
            return "express";
        }
    }

    @Qualifier("secondary")
    public static class SlowChannel implements Channel {
        @Override
        public String deliver() {
            return "standard";
        }
    }

    public static class Client {
        @Autowired
        @Qualifier("primary")
        private Channel channel;

        @Value("${delivery.limit}")
        private int limit;
    }
}
