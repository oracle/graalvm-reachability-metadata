/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;

import org.jboss.weld.util.reflection.SecureReflections;
import org.junit.jupiter.api.Test;

public class SecureReflectionsAnonymous10Test {
    @Test
    void resolvesPrivateDeclaredConstructor() throws Exception {
        Constructor<ReflectionFixture> constructor =
                SecureReflections.getDeclaredConstructor(ReflectionFixture.class, int.class);
        assertThat(constructor.getParameterTypes()).containsExactly(int.class);
        assertThat(Modifier.isPrivate(constructor.getModifiers())).isTrue();
    }
}
