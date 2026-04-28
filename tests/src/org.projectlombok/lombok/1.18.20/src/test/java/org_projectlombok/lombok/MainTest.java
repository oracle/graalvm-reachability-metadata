/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_projectlombok.lombok;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class MainTest {

    @Test
    void runsTheShadowLoadedMainEntryPoint() throws Exception {
        Class<?> mainClass = Class.forName("lombok.launch.Main");
        Method main = mainClass.getDeclaredMethod("main", String[].class);
        main.setAccessible(true);

        PrintStream originalOut = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
            main.invoke(null, new Object[]{new String[]{"--help"}});
        } finally {
            System.setOut(originalOut);
        }

        assertThat(captured.toString(StandardCharsets.UTF_8)).contains("projectlombok.org");
    }
}
