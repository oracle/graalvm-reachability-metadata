/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package tomcat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import org.apache.catalina.authenticator.Constants;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.session.StandardSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardSessionTest {

    @Test
    void serializesAndRestoresSessionStateThroughPublicSessionDataApi() throws Exception {
        StandardManager manager = sessionManager();
        StandardSession session = new StandardSession(manager);
        session.setCreationTime(1_234L);
        session.setMaxInactiveInterval(300);
        session.setNew(false);
        session.setValid(true);
        session.setId("session-under-test", false);
        session.access();
        session.setAuthType("FORM");
        session.setNote(Constants.SESSION_ID_NOTE, "expected-session-id");
        session.setAttribute("message", "hello", false);
        session.setAttribute("counter", Long.valueOf(42), false);

        byte[] serialized = writeSessionData(session);
        StandardSession restored = new StandardSession(manager);
        readSessionData(restored, serialized);

        assertThat(restored.getId()).isEqualTo("session-under-test");
        assertThat(restored.getMaxInactiveInterval()).isEqualTo(300);
        assertThat(restored.isNew()).isFalse();
        assertThat(restored.isValid()).isTrue();
        assertThat(restored.getAuthType()).isEqualTo("FORM");
        assertThat(restored.getPrincipal()).isNull();
        assertThat(restored.getNote(Constants.SESSION_ID_NOTE)).isEqualTo("expected-session-id");
        assertThat(restored.getNote(Constants.FORM_REQUEST_NOTE)).isNull();
        assertThat(restored.getAttribute("message")).isEqualTo("hello");
        assertThat(restored.getAttribute("counter")).isEqualTo(Long.valueOf(42));
    }

    private static StandardManager sessionManager() {
        StandardContext context = new StandardContext();
        context.setName("standard-session-test");
        context.setPath("/standard-session-test");

        StandardManager manager = new StandardManager();
        manager.setPersistAuthentication(true);
        manager.setContext(context);
        context.setManager(manager);
        return manager;
    }

    private static byte[] writeSessionData(StandardSession session) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(buffer)) {
            session.writeObjectData(stream);
        }
        return buffer.toByteArray();
    }

    private static void readSessionData(StandardSession session, byte[] serialized) throws Exception {
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(serialized))) {
            session.readObjectData(stream);
        }
    }
}
