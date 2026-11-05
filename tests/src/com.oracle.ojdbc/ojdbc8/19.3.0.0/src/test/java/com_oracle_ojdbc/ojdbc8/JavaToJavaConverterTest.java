/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.ojdbc8;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import oracle.jdbc.driver.JavaToJavaConverter;
import org.junit.jupiter.api.Test;

public class JavaToJavaConverterTest {
    @Test
    void convertsCommandLineInputThroughNamedNumericTypes() throws Exception {
        PrintStream previous = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            JavaToJavaConverter.main(new String[] {"127", "java.lang.Integer", "java.lang.Long", "java.lang.String"});
        } finally {
            System.setOut(previous);
        }

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("original:\t127", "java.lang.String\tto:\tjava.lang.Integer",
                        "java.lang.Integer\tto:\tjava.lang.Long", "java.lang.Long\tto:\tjava.lang.String",
                        "result:\t127");
    }
}
