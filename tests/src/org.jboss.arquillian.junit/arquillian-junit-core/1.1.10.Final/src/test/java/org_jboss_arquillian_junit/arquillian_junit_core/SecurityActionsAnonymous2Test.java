/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_junit.arquillian_junit_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

public class SecurityActionsAnonymous2Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME = "org.jboss.arquillian.junit.SecurityActions";

    @Test
    void setFieldValueUpdatesPrivateDeclaredField() throws Exception {
        MutableTarget target = new MutableTarget("before");

        invokeSetFieldValue(MutableTarget.class, target, "value", "after");

        assertThat(target.value()).isEqualTo("after");
    }

    private static void invokeSetFieldValue(Class<?> source, Object target, String fieldName, Object value) throws Exception {
        Method setFieldValue = Class.forName(SECURITY_ACTIONS_CLASS_NAME).getDeclaredMethod(
                "setFieldValue", Class.class, Object.class, String.class, Object.class);
        setFieldValue.setAccessible(true);
        setFieldValue.invoke(null, source, target, fieldName, value);
    }

    private static final class MutableTarget {
        private String value;

        private MutableTarget(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }
}
