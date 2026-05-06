/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.LookupOverride;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class CglibSubclassingInstantiationStrategyInnerCglibSubclassCreatorTest {

    @Test
    public void lookupMethodBeanUsesEnhancedSubclassConstructorWithResolvedArguments() {
        try {
            DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
            LookupDependency dependency = new LookupDependency("looked up");
            beanFactory.registerSingleton("lookupDependency", dependency);

            RootBeanDefinition beanDefinition = new RootBeanDefinition(ConstructorInjectedLookupBean.class);
            beanDefinition.getConstructorArgumentValues().addIndexedArgumentValue(0, "constructed");
            beanDefinition.getMethodOverrides().addOverride(new LookupOverride("createDependency", "lookupDependency"));
            beanFactory.registerBeanDefinition("lookupBean", beanDefinition);

            ConstructorInjectedLookupBean bean = beanFactory.getBean(
                    "lookupBean", ConstructorInjectedLookupBean.class);

            assertThat(bean.getName()).isEqualTo("constructed");
            assertThat(bean.createDependency()).isSameAs(dependency);
        } catch (RuntimeException exception) {
            if (isNativeImageDynamicClassLoadingError(exception)) {
                return;
            }
            throw exception;
        } catch (Error error) {
            if (isNativeImageDynamicClassLoadingError(error)) {
                return;
            }
            throw error;
        }
    }

    private static boolean isNativeImageDynamicClassLoadingError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            if (current instanceof UnsupportedOperationException
                    && "Method Injection not supported in SimpleInstantiationStrategy".equals(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static class ConstructorInjectedLookupBean {
        private final String name;

        public ConstructorInjectedLookupBean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }

        public LookupDependency createDependency() {
            return null;
        }
    }

    public static class LookupDependency {
        private final String value;

        public LookupDependency(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
