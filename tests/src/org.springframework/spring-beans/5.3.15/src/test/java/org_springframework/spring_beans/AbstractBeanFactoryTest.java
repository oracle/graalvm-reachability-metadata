/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class AbstractBeanFactoryTest {

    @Test
    public void isTypeMatchResolvesBeanClassThroughTemporaryClassLoader() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        TrackingClassLoader classLoader = new TrackingClassLoader(AbstractBeanFactoryTest.class.getClassLoader(),
                TemporaryClassLoaderBean.class.getName());
        beanFactory.setTempClassLoader(classLoader);

        RootBeanDefinition beanDefinition = new RootBeanDefinition();
        beanDefinition.setBeanClassName(TemporaryClassLoaderBean.class.getName());
        beanFactory.registerBeanDefinition("temporaryClassLoaderBean", beanDefinition);

        assertThat(beanFactory.isTypeMatch("temporaryClassLoaderBean", TemporaryClassLoaderBean.class)).isTrue();
        assertThat(classLoader.hasLoadedTargetClass()).isTrue();
    }

    public static class TemporaryClassLoaderBean {
    }

    private static final class TrackingClassLoader extends ClassLoader {
        private final String targetClassName;
        private boolean loadedTargetClass;

        private TrackingClassLoader(ClassLoader parent, String targetClassName) {
            super(parent);
            this.targetClassName = targetClassName;
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            if (this.targetClassName.equals(name)) {
                this.loadedTargetClass = true;
            }
            return super.loadClass(name);
        }

        private boolean hasLoadedTargetClass() {
            return this.loadedTargetClass;
        }
    }
}
