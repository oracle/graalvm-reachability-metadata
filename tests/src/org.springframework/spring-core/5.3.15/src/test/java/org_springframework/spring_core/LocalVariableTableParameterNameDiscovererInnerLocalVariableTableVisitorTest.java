/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_core;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.core.LocalVariableTableParameterNameDiscoverer;
import org.springframework.core.io.ByteArrayResource;

/** Verifies executable resolution while reading local-variable tables. */
@SuppressWarnings("deprecation")
public class LocalVariableTableParameterNameDiscovererInnerLocalVariableTableVisitorTest {
    @Test
    void resolvesMethodAndConstructorParameterNames() throws Exception {
        LocalVariableTableParameterNameDiscoverer discoverer =
                new LocalVariableTableParameterNameDiscoverer();
        Method method = ByteArrayResource.class.getDeclaredMethod("equals", Object.class);
        Constructor<ByteArrayResource> constructor =
                ByteArrayResource.class.getDeclaredConstructor(byte[].class, String.class);

        assertThat(discoverer.getParameterNames(method)).containsExactly("other");
        assertThat(discoverer.getParameterNames(constructor))
                .containsExactly("byteArray", "description");
    }
}
