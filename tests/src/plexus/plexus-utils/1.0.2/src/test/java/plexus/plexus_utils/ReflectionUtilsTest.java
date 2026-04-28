/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package plexus.plexus_utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import org.codehaus.plexus.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {
    @Test
    void findsDeclaredFieldOnSuperclass() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses(
                "inheritedValue", ReflectionUtilsChildBean.class);

        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("inheritedValue");
        assertThat(field.getDeclaringClass()).isEqualTo(ReflectionUtilsParentBean.class);
    }

    @Test
    void returnsNullWhenFieldDoesNotExistBeforeObjectSuperclass() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses(
                "missingValue", ReflectionUtilsChildBean.class);

        assertThat(field).isNull();
    }

    @Test
    void findsInstanceVoidSetterWithSingleParameter() {
        Method setter = ReflectionUtils.getSetter("displayName", ReflectionUtilsSetterBean.class);

        assertThat(setter).isNotNull();
        assertThat(setter.getName()).isEqualTo("setDisplayName");
        assertThat(setter.getReturnType()).isEqualTo(Void.TYPE);
        assertThat(setter.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void ignoresMethodsThatAreNotBeanSetters() {
        assertThat(ReflectionUtils.getSetter("staticValue", ReflectionUtilsSetterBean.class)).isNull();
        assertThat(ReflectionUtils.getSetter("computedValue", ReflectionUtilsSetterBean.class)).isNull();
        assertThat(ReflectionUtils.getSetter("emptyValue", ReflectionUtilsSetterBean.class)).isNull();
    }
}

class ReflectionUtilsParentBean {
    private String inheritedValue;
}

class ReflectionUtilsChildBean extends ReflectionUtilsParentBean {
}

class ReflectionUtilsSetterBean {
    public void setDisplayName(String displayName) {
    }

    public static void setStaticValue(String staticValue) {
    }

    public String setComputedValue(String computedValue) {
        return computedValue;
    }

    public void setEmptyValue() {
    }
}
