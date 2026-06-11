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
    void defaultMemberAccessorCreatesInvokesReadsAndWritesMembers() throws Exception {
        MemberAccessor memberAccessor =
                Mockito.framework().getPlugins().getDefaultPlugin(MemberAccessor.class);
        Constructor<AccessorTarget> constructor =
                AccessorTarget.class.getDeclaredConstructor(String.class);
        Method greeting = AccessorTarget.class.getDeclaredMethod("greeting", String.class);
        Field value = AccessorTarget.class.getDeclaredField("value");

        AccessorTarget target = (AccessorTarget) memberAccessor.newInstance(constructor, "initial");
        assertThat(memberAccessor.get(value, target)).isEqualTo("initial");

        memberAccessor.set(value, target, "updated");

        assertThat(memberAccessor.invoke(greeting, target, "hello")).isEqualTo("hello updated");
    }

    public static class AccessorTarget {
        private String value;

        public AccessorTarget(String value) {
            this.value = value;
        }

        private String greeting(String prefix) {
            return prefix + " " + value;
        }
    }
}
