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
import org.springframework.util.StopWatch;

public class LocalVariableTableParameterNameDiscovererInnerLocalVariableTableVisitorTest {

    @Test
    void discoversConstructorAndMethodParameterNamesFromLocalVariableTable() throws Exception {
        LocalVariableTableParameterNameDiscoverer discoverer = new LocalVariableTableParameterNameDiscoverer();
        Constructor<StopWatch> constructor = StopWatch.class.getConstructor(String.class);
        Method method = StopWatch.class.getMethod("start", String.class);

        String[] constructorParameterNames = discoverer.getParameterNames(constructor);
        String[] methodParameterNames = discoverer.getParameterNames(method);

        if (isStopWatchClassFileAvailable()) {
            assertThat(constructorParameterNames).containsExactly("id");
            assertThat(methodParameterNames).containsExactly("taskName");
        }
        else {
            assertThat(constructorParameterNames).isNull();
            assertThat(methodParameterNames).isNull();
        }
    }

    private static boolean isStopWatchClassFileAvailable() {
        return StopWatch.class.getResource("StopWatch.class") != null;
    }
}
