/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package plexus.plexus_utils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.codehaus.plexus.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {
    @Test
    void findsFieldsDeclaredOnSuperclasses() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses("inheritedValue", ChildBean.class);

        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("inheritedValue");
        assertThat(field.getDeclaringClass()).isEqualTo(ParentBean.class);
    }

    @Test
    void findsPublicVoidInstanceSetter() {
        Method setter = ReflectionUtils.getSetter("name", ChildBean.class);

        assertThat(setter).isNotNull();
        assertThat(setter.getName()).isEqualTo("setName");
        assertThat(setter.getReturnType()).isEqualTo(Void.TYPE);
        assertThat(setter.getParameterTypes()).containsExactly(String.class);
    }

    public static class ParentBean {
        public String inheritedValue;
    }

    public static class ChildBean extends ParentBean {
        public void setName(String name) {
            inheritedValue = name;
        }
    }
}
