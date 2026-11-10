/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.AutowireCapableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class AbstractBeanDefinitionTest {
    @Test
    void detectsConstructorAutowiringMode() {
        RootBeanDefinition definition = new RootBeanDefinition(ConstructorBean.class);
        definition.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_AUTODETECT);

        assertThat(definition.getResolvedAutowireMode())
                .isEqualTo(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR);
    }

    public static class ConstructorBean {
        public ConstructorBean(String value) {
        }
    }
}
