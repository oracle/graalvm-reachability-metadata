/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.catalina.Session;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardManagerTest {

    @Test
    void unloadsAndLoadsPersistedSessions() throws Exception {
        Path storage = Files.createTempFile("tomcat-sessions", ".ser");
        StandardManager manager = new StandardManager();
        manager.setContext(new StandardContext());
        manager.setSessionIdGenerator(new StandardSessionIdGenerator());
        manager.setPathname(storage.toString());
        Session session = manager.createSession("session-2");
        session.getSession().setAttribute("message", "hello");

        manager.unload();
        manager.load();

        Session restored = manager.findSession("session-2");
        assertThat(restored).isNotNull();
        assertThat(restored.getSession().getAttribute("message")).isEqualTo("hello");
    }
}
