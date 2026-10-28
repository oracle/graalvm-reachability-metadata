/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import javassist.tools.web.Viewer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ViewerTest {
    @Test
    void delegatesAlreadyAvailablePlatformAndViewerTypes() throws Exception {
        Viewer viewer = new Viewer("127.0.0.1", 8080);
        assertSame(String.class, viewer.loadClass("java.lang.String"));
        assertSame(String.class, viewer.loadClass("java.lang.String"));
        assertSame(Viewer.class, viewer.loadClass(Viewer.class.getName()));
        assertEquals("127.0.0.1", viewer.getServer());
        assertEquals(8080, viewer.getPort());
    }

    @Test
    @ResourceLock("SYSTEM_ERR")
    void invokesTheExistingViewerEntryPointAndPrintsItsUsage() throws Throwable {
        Viewer viewer = new Viewer("127.0.0.1", 8080);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        PrintStream previous = System.err;
        try (PrintStream output = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            System.setErr(output);
            viewer.run(Viewer.class.getName(), new String[0]);
        } finally {
            System.setErr(previous);
        }
        assertTrue(bytes.toString(StandardCharsets.UTF_8).contains(
                "Usage: java javassist.tools.web.Viewer <host> <port> class [args ...]"));
    }
}
