/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tomcat_embed.tomcat_embed_core;

import java.nio.file.Path;

import org.apache.catalina.Session;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.session.StandardSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardManagerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsAndRestoresManagedSessions() throws Exception {
        StandardContext context = new StandardContext();
        context.setName("persistent-session-test");
        StandardManager manager = new StandardManager();
        manager.setContext(context);
        manager.setPathname(temporaryDirectory.resolve("sessions.ser").toString());
        StandardSession session = new StandardSession(manager);
        session.setId("persisted-session", false);
        session.setCreationTime(System.currentTimeMillis());
        session.setValid(true);
        session.setAttribute("message", "hello");
        manager.add(session);

        manager.unload();
        manager.load();
        Session restored = manager.findSession("persisted-session");

        assertThat(restored).isNotNull();
        assertThat(restored.getSession().getAttribute("message")).isEqualTo("hello");
        assertThat(temporaryDirectory.resolve("sessions.ser")).doesNotExist();
    }
}
