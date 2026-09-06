/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_context;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import org.springframework.remoting.support.RemoteInvocation;

public class RemoteInvocationTest {

    @Test
    void invokesMatchingPublicMethodOnTargetObject() throws Exception {
        final RemoteInvocation invocation = new RemoteInvocation(
                "formatGreeting",
                new Class<?>[] {String.class, int.class},
                new Object[] {"Spring", 3});
        final GreetingService target = new GreetingService();

        final Object result = invocation.invoke(target);

        assertEquals("Hello Spring Hello Spring Hello Spring", result);
        assertEquals("Spring", target.lastName);
        assertEquals(3, target.lastCount);
    }

    public static final class GreetingService {

        private String lastName;
        private int lastCount;

        public String formatGreeting(String name, int count) {
            this.lastName = name;
            this.lastCount = count;
            return ("Hello " + name + " ").repeat(count).trim();
        }
    }
}
