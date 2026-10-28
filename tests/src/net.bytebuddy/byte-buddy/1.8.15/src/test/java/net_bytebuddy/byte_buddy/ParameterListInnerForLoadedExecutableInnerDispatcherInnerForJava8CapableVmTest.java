/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.method.MethodDescription;
import org.junit.jupiter.api.Test;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParameterListInnerForLoadedExecutableInnerDispatcherInnerForJava8CapableVmTest {
    @Test
    void countsMethodAndConstructorParameters() {
        assertEquals(1, TestFixtures.method("transform").getParameters().size());
        assertEquals(0, TestFixtures.method("run").getParameters().size());
        MethodDescription constructor = TestFixtures.sample().getDeclaredMethods()
                .filter(isConstructor().and(takesArguments(String.class, long.class))).getOnly();
        assertEquals(2, constructor.getParameters().size());
        assertTrue(constructor.getParameters().get(1).getType().asErasure().represents(long.class));
    }
}
