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
import org.apache.catalina.util.StandardSessionIdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardManagerTest {

    @Test
    void persistsAndReloadsManagedSessions(@TempDir Path directory) throws Exception {
        Path storage = directory.resolve("sessions.ser");
        StandardManager writer = manager(storage);
        Session session = writer.createSession("persisted-session");
        session.getSession().setAttribute("message", "restored");

        writer.unload();

        StandardManager reader = manager(storage);
        reader.load();
        Session restored = reader.findSession("persisted-session");

        assertThat(restored).isNotNull();
        assertThat(restored.getSession().getAttribute("message")).isEqualTo("restored");
    }

    private static StandardManager manager(Path storage) {
        StandardContext context = new StandardContext();
        context.setName("persistent-context");
        context.setPath("/persistent");
        StandardManager manager = new StandardManager();
        manager.setContext(context);
        manager.setSessionIdGenerator(new StandardSessionIdGenerator());
        manager.setPathname(storage.toString());
        return manager;
    }
}
