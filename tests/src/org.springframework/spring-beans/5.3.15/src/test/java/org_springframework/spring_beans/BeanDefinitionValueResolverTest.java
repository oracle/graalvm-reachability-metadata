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
import org.springframework.beans.factory.support.ManagedArray;
import org.springframework.beans.factory.support.RootBeanDefinition;

public class BeanDefinitionValueResolverTest {

    @Test
    public void managedArrayPropertyIsResolvedToTypedArray() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        RootBeanDefinition beanDefinition = new RootBeanDefinition(ArrayPropertyBean.class);
        ManagedArray values = new ManagedArray(String.class.getName(), 2);
        values.add("alpha");
        values.add("bravo");
        beanDefinition.getPropertyValues().add("values", values);
        beanFactory.registerBeanDefinition("arrayPropertyBean", beanDefinition);

        ArrayPropertyBean bean = beanFactory.getBean("arrayPropertyBean", ArrayPropertyBean.class);

        assertThat(bean.getValues()).containsExactly("alpha", "bravo");
    }

    public static class ArrayPropertyBean {
        private String[] values;

        public void setValues(String[] values) {
            this.values = values;
        }

        public String[] getValues() {
            return values;
        }
    }
}
