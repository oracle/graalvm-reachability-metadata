/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_database_jdbc.ojdbc11;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import oracle.jdbc.driver.JavaToJavaConverter;
import org.junit.jupiter.api.Test;

public class JavaToJavaConverterTest {
    @Test
    void convertsCommandLineValuesThroughNamedTargetTypes() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            JavaToJavaConverter.main(new String[] {"42", "java.lang.Integer", "java.lang.String"});
        } finally {
            System.setOut(originalOut);
        }

        assertThat(output.toString(StandardCharsets.UTF_8))
                .contains("original:\t42")
                .contains("java.lang.String\tto:\tjava.lang.Integer")
                .contains("java.lang.Integer\tto:\tjava.lang.String")
                .contains("result:\t42");
    }
}
