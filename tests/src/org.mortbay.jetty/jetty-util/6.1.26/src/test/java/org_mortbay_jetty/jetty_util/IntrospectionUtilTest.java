/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mortbay_jetty.jetty_util;

import org.junit.jupiter.api.Test;
import org.mortbay.util.IntrospectionUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class IntrospectionUtilTest {
    @Test
    void findsDeclaredMethodsAndFields() throws Exception {
        Method method = IntrospectionUtil.findMethod(
                IntrospectionDerivedSubject.class,
                "describe",
                new Class[] {String.class},
                false,
                true);
        Field field = IntrospectionUtil.findField(
                IntrospectionDerivedSubject.class,
                "localText",
                CharSequence.class,
                false,
                true);

        assertThat(method.getName()).isEqualTo("describe");
        assertThat(method.getReturnType()).isSameAs(CharSequence.class);
        assertThat(field.getName()).isEqualTo("localText");
        assertThat(field.getType()).isSameAs(CharSequence.class);
    }

    @Test
    void findsInheritedMethodsAndFields() throws Exception {
        Method inheritedMethod = IntrospectionUtil.findMethod(
                IntrospectionDerivedSubject.class,
                "inheritedMethod",
                new Class[] {Integer.class},
                true,
                true);
        Field inheritedField = IntrospectionUtil.findField(
                IntrospectionDerivedSubject.class,
                "inheritedText",
                String.class,
                true,
                true);

        assertThat(inheritedMethod.getDeclaringClass()).isSameAs(IntrospectionBaseSubject.class);
        assertThat(inheritedMethod.getReturnType()).isSameAs(Number.class);
        assertThat(inheritedField.getDeclaringClass()).isSameAs(IntrospectionBaseSubject.class);
        assertThat(inheritedField.getType()).isSameAs(String.class);
    }

    @Test
    void detectsMatchingMethodSignaturesAndFieldNames() throws Exception {
        Method method = IntrospectionUtil.findMethod(
                IntrospectionDerivedSubject.class,
                "describe",
                new Class[] {String.class},
                false,
                true);
        Field field = IntrospectionUtil.findField(
                IntrospectionDerivedSubject.class,
                "localText",
                CharSequence.class,
                false,
                true);

        assertThat(IntrospectionUtil.containsSameMethodSignature(
                method,
                IntrospectionMatchingSubject.class,
                false)).isTrue();
        assertThat(IntrospectionUtil.containsSameFieldName(
                field,
                IntrospectionMatchingSubject.class,
                false)).isTrue();
    }
}

class IntrospectionBaseSubject {
    protected String inheritedText;

    protected Number inheritedMethod(Integer value) {
        return value;
    }
}

class IntrospectionDerivedSubject extends IntrospectionBaseSubject {
    private CharSequence localText;

    public CharSequence describe(String text) {
        localText = text;
        return localText;
    }
}

class IntrospectionMatchingSubject {
    String localText;

    CharSequence describe(String text) {
        return text;
    }
}
