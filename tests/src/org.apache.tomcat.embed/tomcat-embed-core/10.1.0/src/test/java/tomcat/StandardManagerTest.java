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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardManagerTest {
    @TempDir
    Path directory;

    @Test
    void unloadsAndReloadsActiveSessions() throws Exception {
        StandardManager manager = new StandardManager();
        manager.setContext(new StandardContext());
        Path store = directory.resolve("sessions.ser");
        manager.setPathname(store.toString());
        Session session = manager.createSession("persisted-session");
        session.setMaxInactiveInterval(600);
        session.getSession().setAttribute("cart", "notebook");
        session.getSession().setAttribute("answer", Integer.valueOf(42));
        manager.unload();
        assertThat(Files.size(store)).isPositive();
        assertThat(manager.getActiveSessions()).isZero();
        manager.load();
        assertThat(manager.getActiveSessions()).isEqualTo(1);
        Session restored = manager.findSession("persisted-session");
        assertThat(restored.getSession().getAttribute("cart")).isEqualTo("notebook");
        assertThat(restored.getMaxInactiveInterval()).isEqualTo(600);
        assertThat(manager.findSession("persisted-session").getSession().getAttribute("answer"))
                .isEqualTo(42);
        assertThat(Files.exists(store)).isFalse();
        manager.findSession("persisted-session").expire();
    }
}
