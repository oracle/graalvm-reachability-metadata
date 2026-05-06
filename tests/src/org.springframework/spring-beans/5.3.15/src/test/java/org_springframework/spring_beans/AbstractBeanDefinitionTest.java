/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class AbstractBeanDefinitionTest {

    @Test
    public void autowireAutodetectResolvesToByTypeForPublicNoArgConstructor() {
        RootBeanDefinition beanDefinition = autodetectingBeanDefinition(SetterAutowiredBean.class);

        int resolvedAutowireMode = beanDefinition.getResolvedAutowireMode();

        assertThat(resolvedAutowireMode).isEqualTo(AbstractBeanDefinition.AUTOWIRE_BY_TYPE);
    }

    @Test
    public void autowireAutodetectResolvesToConstructorWhenNoPublicNoArgConstructorExists() {
        RootBeanDefinition beanDefinition = autodetectingBeanDefinition(ConstructorAutowiredBean.class);

        int resolvedAutowireMode = beanDefinition.getResolvedAutowireMode();

        assertThat(resolvedAutowireMode).isEqualTo(AbstractBeanDefinition.AUTOWIRE_CONSTRUCTOR);
    }

    private static RootBeanDefinition autodetectingBeanDefinition(Class<?> beanClass) {
        RootBeanDefinition beanDefinition = new RootBeanDefinition(beanClass);
        beanDefinition.setAutowireMode(AbstractBeanDefinition.AUTOWIRE_AUTODETECT);
        return beanDefinition;
    }

    public static class SetterAutowiredBean {

        public SetterAutowiredBean() {
        }
    }

    public static class ConstructorAutowiredBean {

        private final String value;

        public ConstructorAutowiredBean(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
