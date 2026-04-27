/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import javassist.tools.web.Viewer;

import org.junit.jupiter.api.Test;

public class ViewerTest {
    @Test
    void runLoadsClassThroughViewerAndInvokesMainMethod() throws Throwable {
        Viewer viewer = new Viewer("localhost", 0);
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream();
        PrintStream originalError = System.err;

        try (PrintStream capturedError = new PrintStream(errorOutput, true, StandardCharsets.UTF_8)) {
            System.setErr(capturedError);

            viewer.run(Viewer.class.getName(), new String[0]);
        } finally {
            System.setErr(originalError);
        }

        assertThat(errorOutput.toString(StandardCharsets.UTF_8)).contains("Usage: java javassist.tools.web.Viewer");
    }
}
