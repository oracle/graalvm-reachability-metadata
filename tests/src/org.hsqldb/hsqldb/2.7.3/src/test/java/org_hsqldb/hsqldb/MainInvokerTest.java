/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.hsqldb.server.OdbcUtil;
import org.hsqldb.util.MainInvoker;
import org.junit.jupiter.api.Test;

public class MainInvokerTest {
    @Test
    void invokesLibraryStaticMainMethodByClassName() throws Exception {
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        PrintStream previousOut = System.out;

        try (PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8)) {
            System.setOut(output);

            MainInvoker.main(new String[] {OdbcUtil.class.getName(), "41" });
        } finally {
            System.setOut(previousOut);
        }

        assertThat(outputBytes.toString(StandardCharsets.UTF_8)).contains("(\\101)");
    }
}
