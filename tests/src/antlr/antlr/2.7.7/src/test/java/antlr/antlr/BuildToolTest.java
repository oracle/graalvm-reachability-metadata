/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package antlr.antlr;

import antlr.build.Tool;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class BuildToolTest {

    @Test
    void reportsMissingAppOrActionWithoutThrowing() {
        Tool tool = new Tool();
        PrintStream originalErr = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            tool.perform(null, "build");
        } finally {
            System.setErr(originalErr);
        }

        assertThat(err.toString(StandardCharsets.UTF_8)).contains("missing app or action");
    }
}
