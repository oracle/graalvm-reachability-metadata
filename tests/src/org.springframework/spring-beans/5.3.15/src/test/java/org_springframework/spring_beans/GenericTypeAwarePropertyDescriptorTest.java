/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.GenericTypeAwarePropertyDescriptor;

import static org.assertj.core.api.Assertions.assertThat;

public class GenericTypeAwarePropertyDescriptorTest {
    @Test
    void recordsAmbiguousOverloadedWriteMethods() throws Exception {
        Method writeMethod = BeanUtils.findMethod(OverloadedBean.class, "setValue", String.class);

        GenericTypeAwarePropertyDescriptor descriptor = new GenericTypeAwarePropertyDescriptor(
                OverloadedBean.class, "value", null, writeMethod, null);

        assertThat(descriptor.getPropertyType()).isEqualTo(String.class);
        assertThat(descriptor.getWriteMethod()).isEqualTo(writeMethod);
    }

    public static class OverloadedBean {
        public void setValue(String value) {
        }

        public void setValue(Integer value) {
        }
    }
}
