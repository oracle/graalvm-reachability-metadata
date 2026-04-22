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

public class IntrospectionUtilCoverageTest {
    public static class ParentMethods {
        protected String inheritedField = "value";
        protected Number sameFieldName = 1;

        protected void inheritedMethod(Number value) {
        }

        public void sameMethodSignature(CharSequence value) {
        }
    }

    public static class ChildMethods extends ParentMethods {
        public void directMethod(String value) {
        }
    }

    public static class MethodShadow {
        public void sameMethodSignature(CharSequence value) {
        }
    }

    public static class FieldShadow {
        private Integer sameFieldName = 2;
    }

    @Test
    void introspectionUtilFindsMethodsAndFields() throws Exception {
        Method directMethod = IntrospectionUtil.findMethod(ChildMethods.class, "directMethod", new Class<?>[]{String.class}, false, true);
        assertThat(directMethod.getName()).isEqualTo("directMethod");

        Method inheritedMethod = IntrospectionUtil.findMethod(ChildMethods.class, "inheritedMethod", new Class<?>[]{Number.class}, true, true);
        assertThat(inheritedMethod.getName()).isEqualTo("inheritedMethod");

        Field inheritedField = IntrospectionUtil.findField(ChildMethods.class, "inheritedField", String.class, true, true);
        assertThat(inheritedField.getName()).isEqualTo("inheritedField");

        Method signature = ParentMethods.class.getDeclaredMethod("sameMethodSignature", CharSequence.class);
        assertThat(IntrospectionUtil.containsSameMethodSignature(signature, MethodShadow.class, false)).isTrue();

        Field field = ParentMethods.class.getDeclaredField("sameFieldName");
        assertThat(IntrospectionUtil.containsSameFieldName(field, FieldShadow.class, false)).isTrue();
    }
}
