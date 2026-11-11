/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AbstractAutowireCapableBeanFactoryTest {
    @Test
    void callsCustomInitMethodAfterPropertyPopulation() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(Connection.class);
        definition.getPropertyValues().add("host", "database");
        definition.setInitMethodName("connect");
        factory.registerBeanDefinition("connection", definition);
        assertEquals("connected:database", factory.getBean("connection", Connection.class).status);
    }

    public static class Connection {
        private String host;
        private String status;

        public void setHost(String host) {
            this.host = host;
        }

        public void connect() {
            this.status = "connected:" + this.host;
        }
    }
}
