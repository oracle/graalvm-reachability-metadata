/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.jboss.weld.util.reflection.SecureReflections;
import org.junit.jupiter.api.Test;

public class SecureReflectionsAnonymous13Test {
    @Test
    void invokesPublicAndPrivateMethods() throws Exception {
        ReflectionFixture instance = new ReflectionFixture();
        Method greeting = SecureReflections.getMethod(ReflectionFixture.class, "greet", String.class);
        String result = SecureReflections.invoke(instance, greeting, "Ada");
        assertThat(result).isEqualTo("Hello Ada");
        assertThat(instance.label).isEqualTo(result);
        Method secret = SecureReflections.getDeclaredMethod(ReflectionFixture.class, "secret");
        String hidden = SecureReflections.invoke(instance, secret);
        assertThat(hidden).isEqualTo("initial");
    }
}
