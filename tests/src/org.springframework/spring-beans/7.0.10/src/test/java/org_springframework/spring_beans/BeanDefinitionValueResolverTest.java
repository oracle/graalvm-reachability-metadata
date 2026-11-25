/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.ManagedArray;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanDefinitionValueResolverTest {
    @Test
    void resolvesManagedArrayProperty() {
        ManagedArray values = new ManagedArray(String.class.getName(), 2);
        values.add("first");
        values.add("second");
        RootBeanDefinition definition = new RootBeanDefinition(ArrayBean.class);
        definition.getPropertyValues().add("values", values);
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerBeanDefinition("arrayBean", definition);

        assertThat(factory.getBean("arrayBean", ArrayBean.class).getValues())
                .containsExactly("first", "second");
    }

    public static class ArrayBean {
        private String[] values;

        public String[] getValues() {
            return this.values;
        }

        public void setValues(String[] values) {
            this.values = values;
        }
    }
}
