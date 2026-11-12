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
import java.util.List;

import org.apache.catalina.authenticator.Constants;
import org.apache.catalina.authenticator.SavedRequest;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.session.StandardSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardSessionTest {
    @Test
    void persistsSessionAttributesAndAuthentication() throws Exception {
        StandardManager manager = new StandardManager();
        manager.setContext(new StandardContext());
        manager.setPersistAuthentication(true);
        StandardSession session = new StandardSession(manager);
        session.setCreationTime(System.currentTimeMillis());
        session.setValid(true);
        session.setId("session-one");
        session.setMaxInactiveInterval(600);
        session.setAttribute("cart", List.of("book", "pen"));
        session.setAttribute("quantity", 3);
        session.setAuthType("FORM");
        session.setPrincipal(new GenericPrincipal("reader", List.of("customer")));
        session.setNote(Constants.SESSION_ID_NOTE, "expected-session");
        SavedRequest saved = new SavedRequest();
        saved.setMethod("POST");
        saved.setRequestURI("/checkout");
        session.setNote(Constants.FORM_REQUEST_NOTE, saved);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            session.writeObjectData(output);
        }
        StandardSession restored = new StandardSession(manager);
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored.readObjectData(input);
        }
        assertThat(restored.getId()).isEqualTo("session-one");
        assertThat(restored.getCreationTime()).isEqualTo(session.getCreationTime());
        assertThat(restored.isValid()).isTrue();
        assertThat(restored.getAttribute("quantity")).isEqualTo(3);
        assertThat(restored.getMaxInactiveInterval()).isEqualTo(600);
        assertThat(restored.getAttribute("cart")).isEqualTo(List.of("book", "pen"));
        assertThat(restored.getAuthType()).isEqualTo("FORM");
        assertThat(restored.getPrincipal().getName()).isEqualTo("reader");
        assertThat(((GenericPrincipal) restored.getPrincipal()).hasRole("customer")).isTrue();
        assertThat(restored.getNote(Constants.SESSION_ID_NOTE)).isEqualTo("expected-session");
        assertThat(((SavedRequest) restored.getNote(Constants.FORM_REQUEST_NOTE)).getRequestURI())
                .isEqualTo("/checkout");
    }
}
