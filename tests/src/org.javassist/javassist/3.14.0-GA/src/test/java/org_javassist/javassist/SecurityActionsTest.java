/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import java.lang.reflect.Method;

import javassist.util.proxy.RuntimeSupport;
import org.junit.jupiter.api.Test;
import org_javassist.javassist.TestSupport.Greeting;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SecurityActionsTest {
    @Test
    void findsPublicMethodsByTheirJvmDescriptors() {
        Greeting greeting = new Greeting();
        Method method = RuntimeSupport.findMethod(greeting, "greet", "(Ljava/lang/String;)Ljava/lang/String;");
        assertEquals("greet", method.getName());
        assertEquals(Greeting.class, method.getDeclaringClass());
        assertEquals("(Ljava/lang/String;)Ljava/lang/String;", RuntimeSupport.makeDescriptor(method));
    }
}
