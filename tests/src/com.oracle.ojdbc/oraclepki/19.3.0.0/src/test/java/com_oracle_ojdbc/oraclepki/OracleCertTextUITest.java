/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_ojdbc.oraclepki;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import oracle.security.pki.textui.OracleCertTextUI;
import org.junit.jupiter.api.Test;

public class OracleCertTextUITest {
    @Test
    void printsCertificateCommandHelp() throws Exception {
        PrintStream previous = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            assertThat(OracleCertTextUI.command(new String[] {"help"})).isEqualTo(-1);
            assertThat(output.toString(StandardCharsets.UTF_8)).contains("cert:", "display", "create");
        } finally {
            System.setOut(previous);
        }
    }
}
