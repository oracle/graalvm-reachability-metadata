/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package plexus.plexus_utils;

import java.lang.reflect.Field;

import org.codehaus.plexus.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReflectionUtilsTest {
    @Test
    void createsUtilityInstance() {
        ReflectionUtils reflectionUtils = new ReflectionUtils();

        assertThat(reflectionUtils).isNotNull();
    }

    @Test
    void findsFieldsDeclaredOnSuperclasses() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses("inheritedValue", ChildBean.class);

        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("inheritedValue");
        assertThat(field.getDeclaringClass()).isEqualTo(ParentBean.class);
        assertThat(field.getType()).isEqualTo(String.class);
    }

    @Test
    void findsPrivateFieldsDeclaredOnConcreteClass() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses("name", ChildBean.class);

        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("name");
        assertThat(field.getDeclaringClass()).isEqualTo(ChildBean.class);
        assertThat(field.getType()).isEqualTo(String.class);
    }

    @Test
    void returnsNullWhenNoSuperclassDeclaresField() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses("missing", ChildBean.class);

        assertThat(field).isNull();
    }

    public static class ParentBean {
        private String inheritedValue;
    }

    public static class ChildBean extends ParentBean {
        private String name;
    }
}
