/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_jaxb.jaxb_jxc;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.tools.jxc.SchemaGenerator;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.junit.jupiter.api.Test;

public class MessagesTest {
    @Test
    void versionOptionLoadsJxcMessageBundle() throws Exception {
        Locale originalLocale = Locale.getDefault();
        PrintStream originalOut = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (PrintStream printStream = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            Locale.setDefault(Locale.ROOT);
            System.setOut(printStream);

            int exitCode = SchemaGenerator.run(new String[] {"-version"}, MessagesTest.class.getClassLoader());

            assertThat(exitCode).isEqualTo(-1);
        } finally {
            System.setOut(originalOut);
            Locale.setDefault(originalLocale);
        }

        assertThat(output.toString(StandardCharsets.UTF_8)).startsWith("schemagen");
    }
}
