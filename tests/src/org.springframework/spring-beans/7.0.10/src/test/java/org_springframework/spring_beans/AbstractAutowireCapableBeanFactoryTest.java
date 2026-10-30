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

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractAutowireCapableBeanFactoryTest {
    @Test
    void invokesConfiguredCustomInitMethod() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(InitializableBean.class);
        definition.setInitMethodName("initialize");
        factory.registerBeanDefinition("initializable", definition);

        InitializableBean bean = factory.getBean("initializable", InitializableBean.class);

        assertThat(bean.isInitialized()).isTrue();
    }

    public static class InitializableBean {
        private boolean initialized;

        public void initialize() {
            this.initialized = true;
        }

        public boolean isInitialized() {
            return this.initialized;
        }
    }
}
