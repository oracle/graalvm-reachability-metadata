/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.rmi.Remote;

import org.apache.tools.ant.AntClassLoader;
import org.apache.tools.ant.taskdefs.Rmic;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class RmicTest {
    @TempDir
    File directory;

    @Test
    void verifiesExistingRemoteImplementation() {
        Rmic task = new Rmic();
        task.setProject(TestSupport.project(directory));
        task.setBase(directory);
        task.setCompiler("sun");
        task.execute();
        try {
            assertSame(Remote.class, task.getRemoteInterface(RemoteService.class));
            assertTrue(task.isValidRmiRemote(RemoteService.class.getName()));
            assertFalse(task.isValidRmiRemote("java.lang.String"));
        } finally {
            ((AntClassLoader) task.getLoader()).cleanup();
        }
    }

    public static class RemoteService implements Remote {
    }
}
