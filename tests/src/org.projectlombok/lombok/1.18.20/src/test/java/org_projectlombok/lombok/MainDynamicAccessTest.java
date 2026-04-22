/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

public class MainDynamicAccessTest {
    @Test
    void launchMainDelegatesToCoreMain() throws Throwable {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            LombokLaunchTestSupport.invokeStatic(
                    "lombok.launch.Main",
                    "main",
                    new Class<?>[] {String[].class},
                    new Object[] {new String[] {"version"}});
        } finally {
            System.setOut(originalOut);
        }

        assertThat(output.toString(StandardCharsets.UTF_8)).contains(lombok.core.Version.getFullVersion());
    }
}
