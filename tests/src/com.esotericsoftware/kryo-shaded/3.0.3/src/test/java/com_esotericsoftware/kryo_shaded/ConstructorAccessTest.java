/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_esotericsoftware.kryo_shaded;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.esotericsoftware.reflectasm.ConstructorAccess;
import org.junit.jupiter.api.Test;

public class ConstructorAccessTest {
    @Test
    void rejectsNonStaticMemberClassWithPrivateConstructor() {
        assertThatThrownBy(() -> ConstructorAccess.get(PrivateInnerSubject.class))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Non-static member class cannot be created")
                .hasMessageContaining("the enclosing class constructor is private")
                .hasMessageContaining(PrivateInnerSubject.class.getName());
    }

    private class PrivateInnerSubject {
        private PrivateInnerSubject() {
        }
    }
}
