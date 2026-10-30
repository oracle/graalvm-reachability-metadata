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

public class GenericTypeAwarePropertyDescriptorTest {
    @Test
    void introspectsOverloadedWriteMethods() {
        OverloadedBean bean = new OverloadedBean();
        BeanWrapper wrapper = PropertyAccessorFactory.forBeanPropertyAccess(bean);

        wrapper.setPropertyValue("value", "7");

        boolean stringSetterInvoked = "7".equals(bean.stringValue());
        boolean integerSetterInvoked = Integer.valueOf(7).equals(bean.integerValue());
        assertThat(stringSetterInvoked || integerSetterInvoked).isTrue();
    }

    public static class OverloadedBean {
        private String stringValue;
        private Integer integerValue;

        public void setValue(String value) {
            this.stringValue = value;
        }

        public void setValue(Integer value) {
            this.integerValue = value;
        }

        public String stringValue() {
            return this.stringValue;
        }

        public Integer integerValue() {
            return this.integerValue;
        }
    }
}
