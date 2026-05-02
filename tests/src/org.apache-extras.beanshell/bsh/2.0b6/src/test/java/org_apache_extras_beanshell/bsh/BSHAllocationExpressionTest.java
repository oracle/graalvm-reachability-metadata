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

public class BSHAllocationExpressionTest {
    @Test
    void allocatesPrimitiveArrayWithDefinedDimensions() throws Exception {
        Interpreter interpreter = new Interpreter();

        Object result = interpreter.eval("new int[2][3];");

        assertThat(result).isInstanceOf(int[][].class);
        int[][] values = (int[][]) result;
        assertThat(values.length).isEqualTo(2);
        assertThat(values[0].length).isEqualTo(3);
        assertThat(values[0][0]).isZero();
    }

    @Test
    void allocatesObjectArrayWithUndefinedTrailingDimension() throws Exception {
        Interpreter interpreter = new Interpreter();

        Object result = interpreter.eval("new String[2][];");

        assertThat(result).isInstanceOf(String[][].class);
        String[][] values = (String[][]) result;
        assertThat(values.length).isEqualTo(2);
        assertThat(values[0]).isNull();
        assertThat(values[1]).isNull();
    }
}
