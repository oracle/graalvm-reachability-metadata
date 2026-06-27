/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ServiceLocatorFactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ServiceLocatorFactoryBeanTest {

    @Test
    void afterPropertiesSetCreatesServiceLocatorProxy() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("locatedService", new LocatedService("located"));
        ServiceLocator locator = createServiceLocator(beanFactory, null);

        LocatedService service = locator.getService("locatedService");

        assertThat(service.value()).isEqualTo("located");
    }

    @Test
    void customExceptionCanUseMessageAndCauseConstructor() {
        ServiceLocator locator = createServiceLocator(
                new DefaultListableBeanFactory(), MessageAndCauseLocatorException.class);

        assertThatThrownBy(() -> locator.getService("missingService"))
                .isInstanceOf(MessageAndCauseLocatorException.class)
                .hasMessageContaining("missingService")
                .hasCauseInstanceOf(BeansException.class);
    }

    @Test
    void customExceptionCanUseCauseConstructor() {
        ServiceLocator locator = createServiceLocator(
                new DefaultListableBeanFactory(), CauseLocatorException.class);

        assertThatThrownBy(() -> locator.getService("missingService"))
                .isInstanceOf(CauseLocatorException.class)
                .hasCauseInstanceOf(BeansException.class);
    }

    @Test
    void customExceptionCanUseMessageConstructor() {
        ServiceLocator locator = createServiceLocator(
                new DefaultListableBeanFactory(), MessageLocatorException.class);

        assertThatThrownBy(() -> locator.getService("missingService"))
                .isInstanceOf(MessageLocatorException.class)
                .hasMessageContaining("missingService")
                .hasNoCause();
    }

    private static ServiceLocator createServiceLocator(
            DefaultListableBeanFactory beanFactory, Class<? extends Exception> exceptionClass) {
        ServiceLocatorFactoryBean factoryBean = new ServiceLocatorFactoryBean();
        factoryBean.setServiceLocatorInterface(ServiceLocator.class);
        factoryBean.setBeanFactory(beanFactory);
        if (exceptionClass != null) {
            factoryBean.setServiceLocatorExceptionClass(exceptionClass);
        }
        factoryBean.afterPropertiesSet();
        return (ServiceLocator) factoryBean.getObject();
    }

    public interface ServiceLocator {
        LocatedService getService(String beanName);
    }

    public static class LocatedService {
        private final String value;

        public LocatedService(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }

    public static class MessageAndCauseLocatorException extends RuntimeException {
        public MessageAndCauseLocatorException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class CauseLocatorException extends RuntimeException {
        public CauseLocatorException(Throwable cause) {
            super(cause);
        }
    }

    public static class MessageLocatorException extends RuntimeException {
        public MessageLocatorException(String message) {
            super(message);
        }
    }
}
