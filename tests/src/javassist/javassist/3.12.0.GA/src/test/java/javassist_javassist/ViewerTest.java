/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javassist_javassist;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import javassist.tools.web.Viewer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ViewerTest {
    @Test
    void runsViewerMainThroughViewerClassLoader() throws Throwable {
        Viewer viewer = new Viewer("localhost", 0);
        ByteArrayOutputStream standardError = new ByteArrayOutputStream();
        PrintStream originalError = System.err;

        try (PrintStream replacementError = new PrintStream(standardError, true, StandardCharsets.UTF_8)) {
            System.setErr(replacementError);

            viewer.run(Viewer.class.getName(), new String[0]);
        } finally {
            System.setErr(originalError);
        }

        assertThat(standardError.toString(StandardCharsets.UTF_8))
                .contains("Usage: java javassist.tools.web.Viewer");
    }

    @Test
    void loadsPlatformClassesWithViewerClassLoader() throws ClassNotFoundException {
        Viewer viewer = new Viewer("localhost", 0);

        Class<?> loadedClass = viewer.loadClass(String.class.getName());

        assertThat(loadedClass).isSameAs(String.class);
    }
}
