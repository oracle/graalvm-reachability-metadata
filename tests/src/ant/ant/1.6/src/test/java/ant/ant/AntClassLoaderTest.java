/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package ant.ant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.InputStream;
import java.util.Properties;

import org.apache.tools.ant.AntClassLoader;
import org.junit.jupiter.api.Test;

public class AntClassLoaderTest {
    @Test
    void delegatesClassesAndResourcesToParent() throws Exception {
        AntClassLoader loader = new AntClassLoader();
        try {
            assertSame(String.class, loader.loadClass("java.lang.String"));
            assertSame(String.class, loader.forceLoadSystemClass("java.lang.String"));
            for (boolean parentFirst : new boolean[] {true, false}) {
                loader.setParentFirst(parentFirst);
                String resource = "org/apache/tools/ant/taskdefs/defaults.properties";
                assertNotNull(loader.getResource(resource));
                try (InputStream input = loader.getResourceAsStream(resource)) {
                    assertNotNull(input);
                    Properties tasks = new Properties();
                    tasks.load(input);
                    assertEquals("org.apache.tools.ant.taskdefs.Echo", tasks.getProperty("echo"));
                }
            }
        } finally {
            loader.cleanup();
        }
    }
}
