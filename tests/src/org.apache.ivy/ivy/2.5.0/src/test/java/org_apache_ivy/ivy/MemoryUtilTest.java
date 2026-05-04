/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_ivy.ivy;

import org.apache.ivy.util.MemoryUtil;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

public class MemoryUtilTest {

    @Test
    void estimatesSizeOfDefaultConstructibleClass() {
        assertThatCode(() -> MemoryUtil.sizeOf(Object.class)).doesNotThrowAnyException();
    }

    @Test
    void mainLoadsRequestedClassAndPrintsEstimatedSize() throws Exception {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

            MemoryUtil.main(new String[] {Object.class.getName()});
        } finally {
            System.setOut(originalOut);
        }

        String printedSize = output.toString(StandardCharsets.UTF_8).trim();
        assertThat(printedSize).isNotEmpty();
        assertThatCode(() -> Long.parseLong(printedSize)).doesNotThrowAnyException();
    }
}
