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
    public void primitiveArrayAllocationSupportsUndefinedTrailingDimensions() throws Exception {
        Interpreter interpreter = new Interpreter();

        Object result = interpreter.eval("""
                values = new int[2][];
                return values;
                """);

        int[][] values = (int[][]) result;
        assertThat(values.length).isEqualTo(2);
        assertThat(values[0]).isNull();
        assertThat(values[1]).isNull();
    }

    @Test
    public void objectArrayAllocationSupportsUndefinedTrailingDimensions() throws Exception {
        Interpreter interpreter = new Interpreter();

        Object result = interpreter.eval("""
                values = new String[3][];
                return values;
                """);

        String[][] values = (String[][]) result;
        assertThat(values.length).isEqualTo(3);
        assertThat(values[0]).isNull();
        assertThat(values[1]).isNull();
        assertThat(values[2]).isNull();
    }

    @Test
    public void primitiveArrayAllocationCreatesFullyDefinedDimensions() throws Exception {
        Interpreter interpreter = new Interpreter();

        Object result = interpreter.eval("""
                values = new int[2][3];
                values[1][2] = 7;
                return values;
                """);

        int[][] values = (int[][]) result;
        assertThat(values.length).isEqualTo(2);
        assertThat(values[0].length).isEqualTo(3);
        assertThat(values[1].length).isEqualTo(3);
        assertThat(values[1][2]).isEqualTo(7);
    }
}
