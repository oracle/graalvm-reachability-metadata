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

public class SecureReflectionsAnonymous9Test {
    @Test
    void resolvesPublicConstructorWithParameters() throws Exception {
        Constructor<?> constructor = SecureReflections.getConstructor(ReflectionFixture.class, String.class);
        assertThat(constructor.getDeclaringClass()).isEqualTo(ReflectionFixture.class);
        assertThat(constructor.getParameterTypes()).containsExactly(String.class);
        assertThat(Modifier.isPublic(constructor.getModifiers())).isTrue();
    }
}
