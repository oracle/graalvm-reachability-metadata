/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.remoting.support.RemoteInvocation;

import static org.junit.jupiter.api.Assertions.*;

public class RemoteInvocationTest {
    @Test
    void dispatchesNamedRemoteOperation() throws Exception {
        RemoteInvocation invocation = new RemoteInvocation("greet", new Class<?>[]{String.class}, new Object[]{"remote"});
        invocation.addAttribute("requestId", "request-1");
        assertEquals("Hello remote", invocation.invoke(new TestBeans.GreetingService()));
        assertEquals("request-1", invocation.getAttribute("requestId"));
    }
}
