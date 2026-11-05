/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.nio.file.Path;

import org.apache.catalina.Session;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.StandardManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardManagerTest {
    @TempDir
    Path directory;

    @Test
    void persistsActiveSessionsAndRestoresTheirAttributes() throws Exception {
        StandardContext context = new StandardContext();
        context.setName("persistent-sessions");
        StandardManager manager = new StandardManager();
        manager.setContext(context);
        Path persistence = directory.resolve("sessions.ser");
        manager.setPathname(persistence.toString());
        Session session = manager.createSession("persisted-session");
        session.setMaxInactiveInterval(600);
        session.getSession().setAttribute("cart", "notebook");
        manager.unload();
        assertThat(persistence).exists();
        assertThat(manager.getActiveSessions()).isZero();
        manager.load();
        assertThat(manager.getActiveSessions()).isEqualTo(1);
        Session restored = manager.findSession("persisted-session");
        assertThat(restored.getSession().getAttribute("cart")).isEqualTo("notebook");
        assertThat(restored.getMaxInactiveInterval()).isEqualTo(600);
        assertThat(persistence).doesNotExist();
        restored.expire();
    }
}
