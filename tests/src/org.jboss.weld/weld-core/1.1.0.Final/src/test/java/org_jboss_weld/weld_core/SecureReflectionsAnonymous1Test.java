/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_weld.weld_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;

import org.jboss.weld.util.reflection.SecureReflections;
import org.junit.jupiter.api.Test;

public class SecureReflectionsAnonymous1Test {
    @Test
    void findsInheritedPublicField() throws Exception {
        Field field = SecureReflections.getField(ReflectionFixture.class, "inherited");
        assertThat(field.getName()).isEqualTo("inherited");
        assertThat(field.getDeclaringClass()).isEqualTo(ReflectionFixtureParent.class);
        assertThat(field.getType()).isEqualTo(String.class);
    }
}
