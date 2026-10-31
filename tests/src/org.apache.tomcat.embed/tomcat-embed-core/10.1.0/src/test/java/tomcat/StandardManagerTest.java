/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.apache.catalina.Session;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.StandardManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StandardManagerTest {
    @TempDir
    File directory;

    @Test
    void persistsActiveSessionsAndRestoresTheirAttributes() throws Exception {
        File store = new File(directory, "sessions.ser");
        StandardManager manager = new StandardManager();
        manager.setContext(new StandardContext());
        manager.setPathname(store.getAbsolutePath());
        Session original = manager.createSession("customer-session");
        original.getSession().setAttribute("cart", new HashMap<>(Map.of("books", 3)));
        manager.unload();
        assertTrue(store.isFile());
        assertEquals(0, manager.getActiveSessions());
        manager.load();
        assertEquals(1, manager.getActiveSessions());
        Session restored = manager.findSession("customer-session");
        assertEquals(Map.of("books", 3), restored.getSession().getAttribute("cart"));
        assertFalse(store.exists());
        restored.expire();
    }
}
