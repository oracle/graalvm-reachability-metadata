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
    void readsFieldValueBeforeCompoundAssignment() throws Exception {
        Interpreter interpreter = new Interpreter();
        FieldTarget target = new FieldTarget(4);
        interpreter.set("target", target);

        interpreter.eval("target.count += 6;");

        assertThat(target.count).isEqualTo(10);
    }

    public static class FieldTarget {
        public int count;

        public FieldTarget(int count) {
            this.count = count;
        }
    }
}
