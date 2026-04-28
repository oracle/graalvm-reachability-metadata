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

public class BSHArrayInitializerTest {

    @Test
    public void primitiveArrayInitializerCreatesArrayWithConvertedValues() throws Exception {
        Interpreter interpreter = new Interpreter();

        Object result = interpreter.eval("""
                values = new int[] { 1, 2, 3 };
                return values;
                """);

        int[] values = (int[]) result;
        assertThat(values).containsExactly(1, 2, 3);
    }

    @Test
    public void nestedObjectArrayInitializerCreatesMultiDimensionalArray() throws Exception {
        Interpreter interpreter = new Interpreter();

        Object result = interpreter.eval("""
                values = new String[][] {
                    { "alpha", "beta" },
                    { "gamma", "delta" }
                };
                return values;
                """);

        String[][] values = (String[][]) result;
        assertThat(values).hasDimensions(2, 2);
        assertThat(values[0]).containsExactly("alpha", "beta");
        assertThat(values[1]).containsExactly("gamma", "delta");
    }
}
