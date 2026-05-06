/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import static org.assertj.core.api.Assertions.assertThat;

import java.beans.PropertyDescriptor;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapperImpl;

public class GenericTypeAwarePropertyDescriptorTest {

    @Test
    public void beanWrapperIntrospectsWriteOnlyProperty() {
        WriteOnlyBean bean = new WriteOnlyBean();
        BeanWrapperImpl wrapper = new BeanWrapperImpl(bean);

        PropertyDescriptor descriptor = wrapper.getPropertyDescriptor("name");
        wrapper.setPropertyValue("name", "spring");

        assertThat(descriptor.getReadMethod()).isNull();
        assertThat(descriptor.getWriteMethod().getName()).isEqualTo("setName");
        assertThat(descriptor.getPropertyType()).isEqualTo(String.class);
        assertThat(bean.storedName()).isEqualTo("spring");
    }

    public static class WriteOnlyBean {
        private String name;

        public void setName(String name) {
            this.name = name;
        }

        public String storedName() {
            return name;
        }
    }
}
