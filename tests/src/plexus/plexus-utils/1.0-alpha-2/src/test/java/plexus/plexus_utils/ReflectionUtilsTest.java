/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package plexus.plexus_utils;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import org.codehaus.plexus.util.ReflectionUtils;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.assertj.core.api.Assertions.assertThat;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ReflectionUtilsTest {
    @Test
    @Order(1)
    void invokesCompilerGeneratedClassLiteralLookup() throws Throwable {
        MethodHandle classLookup = MethodHandles.privateLookupIn(ReflectionUtils.class, MethodHandles.lookup())
                .findStatic(ReflectionUtils.class, "class$", MethodType.methodType(Class.class, String.class));

        assertThat(classLookup.invoke("java.lang.Object")).isSameAs(Object.class);
    }

    @Test
    @Order(2)
    void returnsNullForMissingFieldDeclaredOnlyByObjectSuperclass() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses("missingValue", DirectBean.class);

        assertThat(field).isNull();
    }

    @Test
    @Order(3)
    void findsFieldsDeclaredOnSuperclasses() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses("inheritedValue", ChildBean.class);

        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("inheritedValue");
        assertThat(field.getDeclaringClass()).isEqualTo(ParentBean.class);
    }

    @Test
    @Order(4)
    void findsFieldsDeclaredOnExactClass() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses("childValue", ChildBean.class);

        assertThat(field).isNotNull();
        assertThat(field.getName()).isEqualTo("childValue");
        assertThat(field.getDeclaringClass()).isEqualTo(ChildBean.class);
    }

    @Test
    @Order(5)
    void returnsNullWhenFieldIsAbsentAcrossHierarchy() {
        Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses("missingValue", ChildBean.class);

        assertThat(field).isNull();
    }

    public static class DirectBean {
    }

    public static class ParentBean {
        public String inheritedValue;
    }

    public static class ChildBean extends ParentBean {
        public String childValue;
    }
}
