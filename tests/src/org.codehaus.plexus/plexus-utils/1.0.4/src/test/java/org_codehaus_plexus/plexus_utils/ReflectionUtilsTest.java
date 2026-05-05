/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.codehaus.plexus.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {
    @Test
    void findsDeclaredFieldInSuperclass() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses("parentValue", ChildBean.class);

        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("parentValue");
        assertThat(field.getDeclaringClass()).isEqualTo(ParentBean.class);
    }

    @Test
    void returnsNullWhenFieldDoesNotExistInHierarchy() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses("missingValue", ChildBean.class);

        assertThat(field).isNull();
    }

    @Test
    void findsPublicInstanceSetter() {
        Method setter = ReflectionUtils.getSetter("name", ChildBean.class);

        assertThat(setter).isNotNull();
        assertThat(setter.getName()).isEqualTo("setName");
        assertThat(setter.getDeclaringClass()).isEqualTo(ParentBean.class);
        assertThat(setter.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void ignoresStaticAndNonVoidSetterCandidates() {
        assertThat(ReflectionUtils.getSetter("status", ChildBean.class)).isNull();
        assertThat(ReflectionUtils.getSetter("count", ChildBean.class)).isNull();
    }

    public static class ParentBean {
        private static String statusValue;

        private String parentValue;

        public void setName(String name) {
            this.parentValue = name;
        }

        public static void setStatus(String status) {
            statusValue = status;
        }
    }

    public static class ChildBean extends ParentBean {
        public String setCount(Integer count) {
            return String.valueOf(count);
        }
    }
}
