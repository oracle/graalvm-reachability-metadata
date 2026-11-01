/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.util.MethodInvoker;

import static org.junit.jupiter.api.Assertions.*;

public class MethodInvokerTest {
    public static class Counter {
        public int twice(Number number) {
            return number.intValue() * 2;
        }
    }

    @Test
    void preparesExactAndAssignableMethods() throws Exception {
        MethodInvoker exact = new MethodInvoker();
        exact.setTargetObject(new TestBeans.GreetingService());
        exact.setTargetMethod("greet");
        exact.setArguments(new Object[]{"Ada"});
        exact.prepare();
        assertEquals("Hello Ada", exact.invoke());
        MethodInvoker assignable = new MethodInvoker();
        assignable.setTargetObject(new Counter());
        assignable.setTargetMethod("twice");
        assignable.setArguments(new Object[]{7});
        assignable.prepare();
        assertEquals(14, assignable.invoke());
    }
}
