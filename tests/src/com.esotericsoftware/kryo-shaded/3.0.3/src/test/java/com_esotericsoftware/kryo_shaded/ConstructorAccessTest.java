/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import com.esotericsoftware.reflectasm.ConstructorAccess;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ConstructorAccessTest {
    @Test
    void rejectsNonStaticMemberClassWithPrivateConstructor() {
        assertThatThrownBy(() -> ConstructorAccess.get(PrivateConstructorOuter.PrivateMember.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Non-static member class cannot be created")
                .hasMessageContaining("enclosing class constructor is private");
    }

    public static class PrivateConstructorOuter {
        @SuppressWarnings("unused")
        private class PrivateMember {
            private PrivateMember() {
            }
        }
    }
}
