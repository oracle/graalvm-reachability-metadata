/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package log4j.log4j;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ControlPanelTest {

    @Test
    void syntheticClassLookupLoadsAnotherChainsawTypeByName() throws Throwable {
        Class<?> controlPanelClass = Class.forName(
                "org.apache.log4j.chainsaw.ControlPanel",
                false,
                ControlPanelTest.class.getClassLoader()
        );
        MethodHandle classLookup = MethodHandles.privateLookupIn(controlPanelClass, MethodHandles.lookup())
                .findStatic(controlPanelClass, "class$", MethodType.methodType(Class.class, String.class));

        Class<?> resolvedClass = (Class<?>) classLookup.invokeExact("org.apache.log4j.chainsaw.ExitAction");

        assertThat(resolvedClass.getName()).isEqualTo("org.apache.log4j.chainsaw.ExitAction");
    }
}
