/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.eclipse.jetty.util.IntrospectionUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectionUtilTest {
    @Test
    void findsDeclaredAndInheritedMembers() throws Exception {
        Method localMethod = IntrospectionUtil.findMethod(
                ChildBean.class, "setLocalValue", new Class<?>[] {Integer.class}, false, true);
        assertThat(localMethod.getDeclaringClass()).isSameAs(ChildBean.class);
        assertThat(IntrospectionUtil.containsSameMethodSignature(localMethod, ChildBean.class, true)).isTrue();

        Method inheritedMethod = IntrospectionUtil.findMethod(
                ChildBean.class, "setInheritedValue", new Class<?>[] {Number.class}, true, true);
        assertThat(inheritedMethod.getDeclaringClass()).isSameAs(ParentBean.class);

        Field localField = IntrospectionUtil.findField(
                ChildBean.class, "localValue", Integer.class, false, true);
        assertThat(localField.getDeclaringClass()).isSameAs(ChildBean.class);
        assertThat(IntrospectionUtil.containsSameFieldName(localField, ChildBean.class, true)).isTrue();

        Field inheritedField = IntrospectionUtil.findField(
                ChildBean.class, "inheritedValue", Number.class, true, true);
        assertThat(inheritedField.getDeclaringClass()).isSameAs(ParentBean.class);
    }

    public static class ParentBean {
        public Number inheritedValue;

        public void setInheritedValue(Number inheritedValue) {
            this.inheritedValue = inheritedValue;
        }
    }

    public static class ChildBean extends ParentBean {
        private Integer localValue;

        private void setLocalValue(Integer localValue) {
            this.localValue = localValue;
        }
    }
}
