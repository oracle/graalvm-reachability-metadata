/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mockito.mockito_core;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.plugins.MemberAccessor;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class InstrumentationMemberAccessorTest {
    @Test
    void accessesConstructorMethodAndFieldsThroughDefaultMemberAccessor() throws Exception {
        MemberAccessor accessor =
                Mockito.framework().getPlugins().getDefaultPlugin(MemberAccessor.class);

        Constructor<AccessorTarget> constructor =
                AccessorTarget.class.getDeclaredConstructor(String.class, int.class);
        AccessorTarget target = (AccessorTarget) accessor.newInstance(constructor, "created", 7);

        Method describe = AccessorTarget.class.getDeclaredMethod("describe", String.class);
        assertThat(accessor.invoke(describe, target, "value")).isEqualTo("value:created:7");

        Field name = AccessorTarget.class.getDeclaredField("name");
        assertThat(accessor.get(name, target)).isEqualTo("created");

        accessor.set(name, target, "updated");
        assertThat(accessor.get(name, target)).isEqualTo("updated");
        assertThat(accessor.invoke(describe, target, "value")).isEqualTo("value:updated:7");
    }

    private static final class AccessorTarget {
        private String name;
        private final int number;

        private AccessorTarget(String name, int number) {
            this.name = name;
            this.number = number;
        }

        private String describe(String prefix) {
            return prefix + ':' + name + ':' + number;
        }
    }
}
