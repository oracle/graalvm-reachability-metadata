/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.io.ByteArrayResource;

/** Verifies parameter-name discovery from class bytecode resources. */
@SuppressWarnings("deprecation")
public class LocalVariableTableParameterNameDiscovererTest {
    @Test
    void discoversMethodParameterNames() throws Exception {
        Method method = ByteArrayResource.class.getDeclaredMethod("equals", Object.class);
        LocalVariableTableParameterNameDiscoverer discoverer =
                new LocalVariableTableParameterNameDiscoverer();

        assertThat(discoverer.getParameterNames(method)).containsExactly("other");
    }
}
