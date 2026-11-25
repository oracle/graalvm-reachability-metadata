/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.PropertyAccessorFactory;

import static org.assertj.core.api.Assertions.assertThat;

public class BeanWrapperImplInnerBeanPropertyHandlerTest {
    @Test
    void invokesJavaBeanGetterAndSetter() {
        Person person = new Person();
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(person);

        wrapper.setPropertyValue("name", "Ada");

        assertThat(wrapper.getPropertyValue("name")).isEqualTo("Ada");
        assertThat(person.getName()).isEqualTo("Ada");
    }

    @Test
    void selectsCompatibleOverloadedWriteOnlySetter() {
        OverloadedBean bean = new OverloadedBean();
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(bean);
        Payload payload = new Payload("parcel");

        wrapper.setPropertyValue("value", 23);
        assertThat(bean.value()).isEqualTo(23);

        wrapper.setPropertyValue("value", payload);
        assertThat(bean.value()).isSameAs(payload);
    }

    public record Payload(String description) {
    }

    public static class OverloadedBean {
        private Object value;

        public void setValue(Integer value) {
            this.value = value;
        }

        public void setValue(Payload value) {
            this.value = value;
        }

        public Object value() {
            return this.value;
        }
    }

    public static class Person {
        private String name;

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
