/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_extras_beanshell.bsh;

import bsh.Interpreter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class LHSTest {

    @Test
    public void compoundAssignmentReadsObjectFieldBeforeWritingIt() throws Exception {
        Interpreter interpreter = new Interpreter();
        MutableCounter counter = new MutableCounter(7);
        interpreter.set("counter", counter);

        Object result = interpreter.eval("""
                counter.value += 5;
                return counter.value;
                """);

        assertThat(result).isEqualTo(12);
        assertThat(counter.value).isEqualTo(12);
    }

    public static class MutableCounter {
        public int value;

        public MutableCounter(int value) {
            this.value = value;
        }
    }
}
