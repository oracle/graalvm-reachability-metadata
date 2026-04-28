/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_thoughtworks_xstream.xstream;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import com.thoughtworks.xstream.core.JVM;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JVMTest {
    @Test
    void mainReportsJvmDiagnosticsIncludingFieldOrderChecks() {
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

            JVM.main(new String[0]);
        } finally {
            System.setOut(originalOut);
        }

        String diagnostics = output.toString(StandardCharsets.UTF_8);
        assertThat(diagnostics)
                .contains("XStream JVM diagnostics")
                .contains("Standard Base64 Codec:")
                .contains("Reverse field order detected for JDK:")
                .contains("Reverse field order detected (only if JVM class itself has been compiled):");
    }
}
