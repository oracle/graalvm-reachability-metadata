/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.jboss.weld.util.reflection.SecureReflections;
import org.junit.jupiter.api.Test;

public class SecureReflectionsAnonymous6Test {
    @Test
    void resolvesPrivateDeclaredMethod() throws Exception {
        Method method = SecureReflections.getDeclaredMethod(ReflectionFixture.class, "secret");
        assertThat(method.getName()).isEqualTo("secret");
        assertThat(method.getDeclaringClass()).isEqualTo(ReflectionFixture.class);
        assertThat(Modifier.isPrivate(method.getModifiers())).isTrue();
    }
}
