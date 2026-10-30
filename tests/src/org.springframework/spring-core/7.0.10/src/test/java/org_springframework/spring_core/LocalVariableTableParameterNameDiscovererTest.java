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
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.io.ByteArrayResource;

/** Verifies default parameter discovery for a dependency method. */
public class LocalVariableTableParameterNameDiscovererTest {
    @Test
    void readsMethodParameterNames() throws Exception {
        Method method = ByteArrayResource.class.getDeclaredMethod("equals", Object.class);
        DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();

        String[] names = discoverer.getParameterNames(method);

        assertThat(names).containsExactly("other");
    }
}
