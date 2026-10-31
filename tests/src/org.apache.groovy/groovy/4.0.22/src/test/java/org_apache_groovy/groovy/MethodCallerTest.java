/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.classgen.asm.MethodCaller;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MethodCallerTest {
    @Test
    void resolvesMethodDescriptorsIncludingAnOverloadedArity() {
        assertEquals("(I)Ljava/lang/String;", MethodCaller.newStatic(Formatter.class, "format", 1).getMethodDescriptor());
        assertEquals("(II)Ljava/lang/String;", MethodCaller.newStatic(Formatter.class, "format", 2).getMethodDescriptor());
        assertEquals("()Ljava/lang/String;", MethodCaller.newVirtual(Formatter.class, "label").getMethodDescriptor());
    }

    public static class Formatter {
        public static String format(int number) {
            return Integer.toString(number);
        }

        public static String format(int first, int second) {
            return first + ":" + second;
        }

        public String label() {
            return "value";
        }
    }
}
