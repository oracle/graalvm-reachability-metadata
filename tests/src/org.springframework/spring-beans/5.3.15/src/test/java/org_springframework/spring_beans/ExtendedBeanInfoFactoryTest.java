/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.beans.BeanInfo;
import java.beans.PropertyDescriptor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.ExtendedBeanInfoFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class ExtendedBeanInfoFactoryTest {
    @Test
    void introspectsNonVoidReturningSetter() throws Exception {
        BeanInfo beanInfo = new ExtendedBeanInfoFactory().getBeanInfo(FluentBean.class);

        assertThat(beanInfo).isNotNull();
        String[] propertyNames = new String[beanInfo.getPropertyDescriptors().length];
        int index = 0;
        for (PropertyDescriptor descriptor : beanInfo.getPropertyDescriptors()) {
            propertyNames[index++] = descriptor.getName();
        }
        assertThat(propertyNames).contains("name");
    }

    public static class FluentBean {
        private String name;

        public String getName() {
            return this.name;
        }

        public FluentBean setName(String name) {
            this.name = name;
            return this;
        }
    }
}
