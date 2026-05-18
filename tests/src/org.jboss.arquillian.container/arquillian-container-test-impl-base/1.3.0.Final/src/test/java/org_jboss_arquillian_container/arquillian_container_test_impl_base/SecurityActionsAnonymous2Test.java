/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_impl_base;

import java.lang.reflect.Method;

import org.jboss.arquillian.container.test.impl.RemoteExtensionLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous2Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME =
            "org.jboss.arquillian.container.test.impl.SecurityActions";

    @Test
    void setFieldValueUpdatesPrivateFieldOnTargetObject() throws Exception {
        MutableHolder holder = new MutableHolder("initial");

        invokeSetFieldValue(MutableHolder.class, holder, "value", "updated");

        assertThat(holder.value()).isEqualTo("updated");
    }

    private static void invokeSetFieldValue(
            Class<?> source,
            Object target,
            String fieldName,
            Object value) throws Exception {
        Method setFieldValueMethod = securityActionsClass().getDeclaredMethod(
                "setFieldValue",
                Class.class,
                Object.class,
                String.class,
                Object.class);
        setFieldValueMethod.setAccessible(true);
        setFieldValueMethod.invoke(null, source, target, fieldName, value);
    }

    private static Class<?> securityActionsClass() throws ClassNotFoundException {
        return RemoteExtensionLoader.class.getClassLoader().loadClass(SECURITY_ACTIONS_CLASS_NAME);
    }

    private static final class MutableHolder {
        private String value;

        private MutableHolder(String value) {
            this.value = value;
        }

        private String value() {
            return value;
        }
    }
}
