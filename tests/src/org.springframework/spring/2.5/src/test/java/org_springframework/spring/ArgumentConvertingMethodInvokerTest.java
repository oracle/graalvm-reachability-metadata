/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring;

import org.junit.jupiter.api.Test;
import org.springframework.beans.support.ArgumentConvertingMethodInvoker;

import static org.junit.jupiter.api.Assertions.*;

public class ArgumentConvertingMethodInvokerTest {
    public static class Calculator {
        public int triple(int value) {
            return value * 3;
        }
    }

    @Test
    void convertsStringArgumentBeforeInvokingMethod() throws Exception {
        ArgumentConvertingMethodInvoker invoker = new ArgumentConvertingMethodInvoker();
        invoker.setTargetObject(new Calculator());
        invoker.setTargetMethod("triple");
        invoker.setArguments(new Object[]{"14"});
        invoker.prepare();
        assertEquals(42, invoker.invoke());
    }
}
