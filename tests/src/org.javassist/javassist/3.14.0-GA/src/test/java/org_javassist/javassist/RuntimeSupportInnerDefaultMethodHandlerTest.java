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

public class RuntimeSupportInnerDefaultMethodHandlerTest {
    @Test
    void defaultHandlerProceedsToTheOriginalMethodOnAnExistingObject() throws Throwable {
        Greeting greeting = new Greeting();
        Method method = RuntimeSupport.findMethod(greeting, "greet", "(Ljava/lang/String;)Ljava/lang/String;");
        assertEquals("Hello, Ada", RuntimeSupport.default_interceptor.invoke(greeting, method, method, new Object[]{"Ada"}));
        assertEquals("Hello, Grace", RuntimeSupport.default_interceptor.invoke(greeting, method, method, new Object[]{"Grace"}));
    }
}
