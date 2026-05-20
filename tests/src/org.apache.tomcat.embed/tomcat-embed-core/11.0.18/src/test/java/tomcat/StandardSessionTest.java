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

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.session.StandardSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardSessionTest {

    @Test
    void serializesAndDeserializesSessionData() throws Exception {
        StandardManager manager = new StandardManager();
        manager.setContext(new StandardContext());
        StandardSession session = new StandardSession(manager);
        session.setValid(true);
        session.setId("session-1");
        session.setAttribute("message", "hello");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
            session.writeObjectData(stream);
        }

        StandardSession restored = new StandardSession(manager);
        try (ObjectInputStream stream = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored.readObjectData(stream);
        }

        assertThat(restored.getId()).isEqualTo("session-1");
        assertThat(restored.getAttribute("message")).isEqualTo("hello");
    }
}
