/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_immutables.value;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;

import org.immutables.value.internal.$guava$.base.$Throwables;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThrowablesTest {

    @Test
    void utilityMethodsExposeCausalChainAndStackTraceText() {
        IllegalArgumentException root = new IllegalArgumentException("root cause");
        IllegalStateException middle = new IllegalStateException("middle cause", root);
        RuntimeException top = new RuntimeException("top level", middle);

        assertThat($Throwables.getRootCause(top)).isSameAs(root);
        assertThat($Throwables.getCausalChain(top)).containsExactly(top, middle, root);
        assertThat($Throwables.getStackTraceAsString(top))
                .contains(RuntimeException.class.getName())
                .contains("top level")
                .contains(ThrowablesTest.class.getName());
    }

    @Test
    void invokeAccessibleNonThrowingMethodInvokesProvidedMethod() throws Throwable {
        MethodHandle invokeAccessibleNonThrowingMethod = MethodHandles.privateLookupIn($Throwables.class, MethodHandles.lookup())
                .findStatic(
                        $Throwables.class,
                        "invokeAccessibleNonThrowingMethod",
                        MethodType.methodType(Object.class, Method.class, Object.class, Object[].class)
                );
        Method slice = SliceTarget.class.getMethod("slice", int.class, int.class);

        Object result = (Object) invokeAccessibleNonThrowingMethod.invokeExact(
                slice,
                (Object) new SliceTarget(),
                new Object[]{1, 5}
        );

        assertThat(result).isEqualTo("over");
    }

    public static final class SliceTarget {

        public String slice(int beginIndex, int endIndex) {
            return "coverage".substring(beginIndex, endIndex);
        }
    }
}
