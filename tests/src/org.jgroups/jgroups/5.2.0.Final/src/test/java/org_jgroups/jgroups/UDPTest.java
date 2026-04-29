/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jgroups.jgroups;

import org.jgroups.protocols.UDP;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

public class UDPTest extends UDP {
    @Test
    void findMethodReturnsNullWhenDeclaredMethodIsUnavailable() {
        Method method = findMethod(UDP.class, "missingDeclaredUdpHook", String.class);

        assertThat(method).isNull();
    }
}
