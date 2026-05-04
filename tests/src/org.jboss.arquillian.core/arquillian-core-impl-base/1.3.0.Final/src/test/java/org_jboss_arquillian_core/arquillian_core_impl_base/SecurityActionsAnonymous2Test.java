/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

public class SecurityActionsAnonymous2Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME = "org.jboss.arquillian.core.impl.SecurityActions";

    @Test
    void setFieldValueUpdatesPrivateFieldByName() throws Exception {
        DynamicAccessTarget target = new DynamicAccessTarget("initial");

        invokeSetFieldValue(DynamicAccessTarget.class, target, "value", "updated");

        assertThat(target.value()).isEqualTo("updated");
    }

    private static void invokeSetFieldValue(Class<?> source, Object target, String fieldName, Object value) throws Exception {
        Method setFieldValue = securityActionsClass().getDeclaredMethod(
                "setFieldValue", Class.class, Object.class, String.class, Object.class);
        setFieldValue.setAccessible(true);
        setFieldValue.invoke(null, source, target, fieldName, value);
    }

    private static Class<?> securityActionsClass() throws ClassNotFoundException {
        return Class.forName(SECURITY_ACTIONS_CLASS_NAME);
    }

    private static final class DynamicAccessTarget {
        private String value;

        private DynamicAccessTarget(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }
}
