/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_slf4j.jcl_over_slf4j;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.apache.commons.logging.impl.SLF4JLogFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SLF4JLogFactoryTest {

    @Test
    void releasePrintsCompatibilityWarning() {
        SLF4JLogFactory factory = new SLF4JLogFactory();
        ByteArrayOutputStream capturedOutput = new ByteArrayOutputStream();
        PrintStream originalOutput = System.out;

        System.setOut(new PrintStream(capturedOutput, true, StandardCharsets.UTF_8));
        try {
            factory.release();
        }
        finally {
            System.setOut(originalOutput);
        }

        String output = capturedOutput.toString(StandardCharsets.UTF_8);

        assertThat(output)
                .contains("org.apache.commons.logging.impl.SLF4JLogFactory")
                .contains("#release() was invoked")
                .contains("codes.html#release");
    }
}
