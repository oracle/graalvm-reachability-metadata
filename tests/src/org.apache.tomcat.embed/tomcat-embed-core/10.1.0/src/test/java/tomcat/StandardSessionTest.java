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
import java.util.ArrayList;
import java.util.List;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.session.StandardSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class StandardSessionTest {
    @Test
    void roundTripsSessionDataUsingPublicPersistenceApi() throws Exception {
        StandardManager manager = new StandardManager();
        manager.setContext(new StandardContext());
        StandardSession original = (StandardSession) manager.createSession("cart-session");
        original.setMaxInactiveInterval(1800);
        original.setAttribute("items", new ArrayList<>(List.of("book", "pen")));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            original.writeObjectData(output);
        }
        StandardSession restored = new StandardSession(manager);
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored.readObjectData(input);
        }
        assertEquals("cart-session", restored.getId());
        assertEquals(1800, restored.getMaxInactiveInterval());
        assertEquals(List.of("book", "pen"), restored.getAttribute("items"));
        assertEquals(original.getCreationTime(), restored.getCreationTime());
        assertTrue(restored.isValid());
    }
}
