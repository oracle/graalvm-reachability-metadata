/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ParameterTypesTest {
    @Test
    void packsOmittedSingleAndMultipleVarargs() {
        Joiner joiner = new Joiner();
        assertEquals("", InvokerHelper.invokeMethod(joiner, "join", new Object[]{}));
        assertEquals("a", InvokerHelper.invokeMethod(joiner, "join", new Object[]{"a"}));
        assertEquals("a/b/c", InvokerHelper.invokeMethod(joiner, "join", new Object[]{"a", "b", "c"}));
        assertEquals("prefix:", InvokerHelper.invokeMethod(joiner, "prefixed", new Object[]{"prefix"}));
    }

    public static class Joiner {
        public String join(String... values) {
            return String.join("/", values);
        }

        public String prefixed(String prefix, String... values) {
            return prefix + ":" + String.join("/", values);
        }
    }
}
