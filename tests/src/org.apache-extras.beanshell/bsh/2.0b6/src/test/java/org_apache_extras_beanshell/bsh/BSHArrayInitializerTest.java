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
    void evaluatesTypedPrimitiveArrayInitializer() throws Exception {
        Interpreter interpreter = new Interpreter();

        Object result = interpreter.eval("""
                int[] values = {1, 2, 3};
                values;
                """);

        assertThat(result).isInstanceOf(int[].class);
        assertThat((int[]) result).containsExactly(1, 2, 3);
    }
}
