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
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class AutowiredAnnotationBeanPostProcessorTest {

    @Test
    void resolvesAutowiredConstructorDeclaredOnUserClassForProxySubclass() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        DependencyService dependencyService = new DependencyService("spring-beans");
        beanFactory.registerSingleton("dependencyService", dependencyService);
        beanFactory.registerBeanDefinition("constructorInjectedBean",
                new RootBeanDefinition(ConstructorInjectedBean$$SpringCGLIB.class));

        AutowiredAnnotationBeanPostProcessor postProcessor = new AutowiredAnnotationBeanPostProcessor();
        postProcessor.setBeanFactory(beanFactory);
        beanFactory.addBeanPostProcessor(postProcessor);

        ConstructorInjectedBean bean = beanFactory.getBean("constructorInjectedBean", ConstructorInjectedBean.class);

        assertThat(bean).isInstanceOf(ConstructorInjectedBean$$SpringCGLIB.class);
        assertThat(bean.getDependencyService()).isSameAs(dependencyService);
        assertThat(bean.getDependencyService().getName()).isEqualTo("spring-beans");
    }

    public static class ConstructorInjectedBean {
        private final DependencyService dependencyService;

        @Autowired
        public ConstructorInjectedBean(DependencyService dependencyService) {
            this.dependencyService = dependencyService;
        }

        DependencyService getDependencyService() {
            return this.dependencyService;
        }
    }

    public static class ConstructorInjectedBean$$SpringCGLIB extends ConstructorInjectedBean {
        public ConstructorInjectedBean$$SpringCGLIB(DependencyService dependencyService) {
            super(dependencyService);
        }
    }

    public static class DependencyService {
        private final String name;

        DependencyService(String name) {
            this.name = name;
        }

        String getName() {
            return this.name;
        }
    }
}
