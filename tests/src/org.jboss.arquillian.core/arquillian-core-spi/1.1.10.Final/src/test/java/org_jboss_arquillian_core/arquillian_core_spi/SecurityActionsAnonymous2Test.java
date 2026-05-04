/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_core.arquillian_core_spi;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class SecurityActionsAnonymous2Test {
    private String message = "original";

    @Test
    void setsPrivateFieldValue() throws Exception {
        setFieldValue(SecurityActionsAnonymous2Test.class, this, "message", "updated");

        assertThat(message).isEqualTo("updated");
    }

    private static void setFieldValue(
            Class<?> source,
            Object target,
            String fieldName,
            Object value) throws Exception {
        Class<?> securityActions = Class.forName("org.jboss.arquillian.core.spi.SecurityActions");
        Method setFieldValue = securityActions.getDeclaredMethod(
                "setFieldValue",
                Class.class,
                Object.class,
                String.class,
                Object.class);
        setFieldValue.setAccessible(true);
        setFieldValue.invoke(null, source, target, fieldName, value);
    }
}
