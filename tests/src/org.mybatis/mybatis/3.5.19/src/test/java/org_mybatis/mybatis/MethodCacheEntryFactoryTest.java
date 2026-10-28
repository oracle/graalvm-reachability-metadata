/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis;

import org.apache.ibatis.scripting.xmltags.OgnlCache;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MethodCacheEntryFactoryTest {
    @Test
    void resolvesInheritedMethodDuringExpressionEvaluation() {
        assertEquals("hello Ada", OgnlCache.getValue("greet('Ada')", new Greeting()));
    }

    public static class BaseGreeting {
        public String greet(String name) {
            return "hello " + name;
        }
    }

    public static class Greeting extends BaseGreeting {
    }
}
