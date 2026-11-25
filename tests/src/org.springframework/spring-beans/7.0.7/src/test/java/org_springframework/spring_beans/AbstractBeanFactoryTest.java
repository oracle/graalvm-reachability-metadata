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
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AbstractBeanFactoryTest {
    @Test
    void checksBeanTypeWithTemporaryLoaderBeforeInstantiation() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.setTempClassLoader(getClass().getClassLoader());
        RootBeanDefinition definition = new RootBeanDefinition();
        definition.setBeanClassName(Worker.class.getName());
        factory.registerBeanDefinition("worker", definition);
        assertTrue(factory.isTypeMatch("worker", Runnable.class));
        Worker worker = factory.getBean("worker", Worker.class);
        worker.run();
        assertEquals(1, worker.runs);
    }

    public static class Worker implements Runnable {
        private int runs;

        @Override
        public void run() {
            this.runs++;
        }
    }
}
