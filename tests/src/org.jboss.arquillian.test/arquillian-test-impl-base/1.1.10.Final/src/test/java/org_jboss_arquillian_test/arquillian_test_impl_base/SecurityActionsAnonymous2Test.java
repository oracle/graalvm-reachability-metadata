/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_arquillian_test.arquillian_test_impl_base;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.jupiter.api.Test;

public class SecurityActionsAnonymous2Test {
    private static final String SECURITY_ACTIONS_CLASS_NAME =
        "org.jboss.arquillian.test.impl.enricher.resource.SecurityActions";

    @Test
    void setFieldValueUpdatesPrivateField() throws Throwable {
        MethodHandle setFieldValue = securityActionsLookup().findStatic(securityActionsType(), "setFieldValue",
            MethodType.methodType(void.class, Class.class, Object.class, String.class, Object.class));
        MutableTarget target = new MutableTarget();

        setFieldValue.invoke(MutableTarget.class, target, "value", "updated");

        assertThat(target.value()).isEqualTo("updated");
    }

    private static MethodHandles.Lookup securityActionsLookup() throws ClassNotFoundException, IllegalAccessException {
        return MethodHandles.privateLookupIn(securityActionsType(), MethodHandles.lookup());
    }

    private static Class<?> securityActionsType() throws ClassNotFoundException {
        return Class.forName(SECURITY_ACTIONS_CLASS_NAME);
    }

    private static final class MutableTarget {
        private String value = "original";

        String value() {
            return value;
        }
    }
}
