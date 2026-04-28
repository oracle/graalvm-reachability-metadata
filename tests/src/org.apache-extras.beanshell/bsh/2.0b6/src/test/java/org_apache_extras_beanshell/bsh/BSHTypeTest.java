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

public class BSHTypeTest {

    @Test
    public void typedObjectArrayDeclarationResolvesArrayClass() throws Exception {
        Interpreter interpreter = new Interpreter();

        Object result = interpreter.eval("""
                String[][] values;
                values = new String[2][1];
                values[0][0] = "alpha";
                values[1][0] = "beta";
                return values;
                """);

        String[][] values = (String[][]) result;
        assertThat(values).hasDimensions(2, 1);
        assertThat(values[0][0]).isEqualTo("alpha");
        assertThat(values[1][0]).isEqualTo("beta");
    }

    @Test
    public void typedPrimitiveArrayDeclarationResolvesArrayClass() throws Exception {
        Interpreter interpreter = new Interpreter();

        Object result = interpreter.eval("""
                int[][] values;
                values = new int[1][2];
                values[0][0] = 3;
                values[0][1] = 5;
                return values;
                """);

        int[][] values = (int[][]) result;
        assertThat(values).hasDimensions(1, 2);
        assertThat(values[0]).containsExactly(3, 5);
    }
}
