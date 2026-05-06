/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.config.ServiceLocatorFactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class ServiceLocatorFactoryBeanTest {

    @Test
    public void afterPropertiesSetCreatesServiceLocatorProxy() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("sampleService", new RootBeanDefinition(SampleService.class));
        ServiceLocatorFactoryBean factoryBean = serviceLocatorFactoryBean(beanFactory);

        SampleServiceLocator locator = (SampleServiceLocator) factoryBean.getObject();
        SampleService service = locator.getService();

        assertThat(factoryBean.getObjectType()).isEqualTo(SampleServiceLocator.class);
        assertThat(factoryBean.isSingleton()).isTrue();
        assertThat(service.message()).isEqualTo("located");
        assertThat(locator.toString()).contains(SampleServiceLocator.class.getName());
    }

    @Test
    public void serviceLocatorExceptionUsesStringAndThrowableConstructor() {
        ServiceLocatorFactoryBean factoryBean = serviceLocatorFactoryBean(new DefaultListableBeanFactory());
        factoryBean.setServiceLocatorExceptionClass(StringAndThrowableLocatorException.class);

        SampleServiceLocator locator = (SampleServiceLocator) factoryBean.getObject();

        assertThatThrownBy(locator::getService)
                .isInstanceOf(StringAndThrowableLocatorException.class)
                .hasCauseInstanceOf(NoSuchBeanDefinitionException.class)
                .hasMessageContaining(SampleService.class.getName());
    }

    @Test
    public void serviceLocatorExceptionFallsBackToThrowableConstructor() {
        ServiceLocatorFactoryBean factoryBean = serviceLocatorFactoryBean(new DefaultListableBeanFactory());
        factoryBean.setServiceLocatorExceptionClass(ThrowableOnlyLocatorException.class);

        SampleServiceLocator locator = (SampleServiceLocator) factoryBean.getObject();

        assertThatThrownBy(locator::getService)
                .isInstanceOf(ThrowableOnlyLocatorException.class)
                .hasCauseInstanceOf(NoSuchBeanDefinitionException.class);
    }

    @Test
    public void serviceLocatorExceptionFallsBackToStringConstructor() {
        ServiceLocatorFactoryBean factoryBean = serviceLocatorFactoryBean(new DefaultListableBeanFactory());
        factoryBean.setServiceLocatorExceptionClass(StringOnlyLocatorException.class);

        SampleServiceLocator locator = (SampleServiceLocator) factoryBean.getObject();

        assertThatThrownBy(locator::getService)
                .isInstanceOf(StringOnlyLocatorException.class)
                .hasNoCause()
                .hasMessageContaining(SampleService.class.getName());
    }

    private static ServiceLocatorFactoryBean serviceLocatorFactoryBean(DefaultListableBeanFactory beanFactory) {
        ServiceLocatorFactoryBean factoryBean = new ServiceLocatorFactoryBean();
        factoryBean.setServiceLocatorInterface(SampleServiceLocator.class);
        factoryBean.setBeanFactory(beanFactory);
        factoryBean.afterPropertiesSet();
        return factoryBean;
    }

    public interface SampleServiceLocator {
        SampleService getService();
    }

    public static class SampleService {
        public String message() {
            return "located";
        }
    }

    public static class StringAndThrowableLocatorException extends RuntimeException {
        public StringAndThrowableLocatorException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class ThrowableOnlyLocatorException extends RuntimeException {
        public ThrowableOnlyLocatorException(Throwable cause) {
            super(cause);
        }
    }

    public static class StringOnlyLocatorException extends RuntimeException {
        public StringOnlyLocatorException(String message) {
            super(message);
        }
    }
}
