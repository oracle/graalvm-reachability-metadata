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
