/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;

import org.jboss.weld.util.reflection.SecureReflections;
import org.junit.jupiter.api.Test;

public class SecureReflectionsAnonymous12Test {
    @Test
    void listsPublicAndPrivateDeclaredConstructors() {
        Constructor<?>[] constructors = SecureReflections.getDeclaredConstructors(ReflectionFixture.class);
        assertThat(constructors).hasSize(3);
        assertThat(constructors).extracting(Constructor::getParameterCount).containsExactlyInAnyOrder(0, 1, 1);
        assertThat(constructors).anySatisfy(constructor ->
                assertThat(constructor.getParameterTypes()).containsExactly(int.class));
    }
}
