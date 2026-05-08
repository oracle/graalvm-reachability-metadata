/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_container.arquillian_container_test_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.jboss.arquillian.container.test.spi.util.TestRunners;
import org.junit.jupiter.api.Test;

public class SecurityActionsAnonymous2Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME =
            "org.jboss.arquillian.container.test.spi.util.SecurityActions";

    @Test
    void setFieldValueUpdatesPrivateFieldOnTargetInstance() throws Exception {
        MutableTarget target = new MutableTarget();

        setFieldValue(MutableTarget.class, target, "message", "updated by security action");

        assertThat(target.message()).isEqualTo("updated by security action");
    }

    private static void setFieldValue(
            Class<?> source,
            Object target,
            String fieldName,
            Object value) throws Exception {
        Method method = securityActionsClass().getDeclaredMethod(
                "setFieldValue",
                Class.class,
                Object.class,
                String.class,
                Object.class);
        method.setAccessible(true);
        method.invoke(null, source, target, fieldName, value);
    }

    private static Class<?> securityActionsClass() throws ClassNotFoundException {
        return TestRunners.class.getClassLoader().loadClass(SECURITY_ACTIONS_CLASS_NAME);
    }

    private static final class MutableTarget {
        private String message = "original";

        private String message() {
            return message;
        }
    }
}
