/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus.plexus_utils;

import org.codehaus.plexus.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {
    @Test
    void findsDeclaredFieldsOnConcreteClasses() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses("childValue", ChildBean.class);

        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("childValue");
        assertThat(field.getDeclaringClass()).isEqualTo(ChildBean.class);
    }

    @Test
    void searchesSuperclassesWhenFieldIsNotDeclaredOnConcreteClass() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses("parentValue", ChildBean.class);

        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("parentValue");
        assertThat(field.getDeclaringClass()).isEqualTo(ParentBean.class);
    }

    @Test
    void returnsNullWhenFieldIsAbsentFromClassHierarchy() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses("missingValue", ChildBean.class);

        assertThat(field).isNull();
    }

    @Test
    void findsPublicInstanceSetterByBeanFieldName() {
        Method setter = ReflectionUtils.getSetter("label", ChildBean.class);

        assertThat(setter).isNotNull();
        assertThat(setter.getName()).isEqualTo("setLabel");
        assertThat(setter.getDeclaringClass()).isEqualTo(ParentBean.class);
        assertThat(setter.getParameterTypes()).containsExactly(String.class);
    }

    @Test
    void ignoresStaticSetterLikeMethods() {
        Method setter = ReflectionUtils.getSetter("staticLabel", ChildBean.class);

        assertThat(setter).isNull();
    }

    public static class ParentBean {
        private String parentValue;
        private String label;

        public void setLabel(String label) {
            this.label = label;
        }
    }

    public static class ChildBean extends ParentBean {
        private String childValue;

        public static void setStaticLabel(String ignored) {
        }
    }
}
