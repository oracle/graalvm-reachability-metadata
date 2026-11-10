/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import java.lang.reflect.Method;
import java.security.AccessController;

import org.apache.ibatis.ognl.security.UserMethod;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class UserMethodTest {
    @Test
    void executesAUserMethodAsAPrivilegedAction() throws Exception {
        Counter counter = new Counter();
        Method add = Counter.class.getMethod("add", int.class);
        UserMethod action = new UserMethod(counter, add, new Object[]{3});
        assertEquals(3, AccessController.doPrivileged(action));
        assertEquals(6, AccessController.doPrivileged(action));
        assertEquals(6, counter.total);
    }

    public static class Counter {
        private int total;

        public int add(int amount) {
            total += amount;
            return total;
        }
    }
}
