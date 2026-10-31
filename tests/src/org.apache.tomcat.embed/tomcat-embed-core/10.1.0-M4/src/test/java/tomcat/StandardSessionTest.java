package tomcat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import org.apache.catalina.core.StandardContext;
import org.apache.catalina.realm.GenericPrincipal;
import org.apache.catalina.session.StandardManager;
import org.apache.catalina.session.StandardSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class StandardSessionTest {
    @Test
    void roundTripsSessionDataIncludingAuthentication() throws Exception {
        StandardContext context = new StandardContext();
        context.setName("sessions");
        StandardManager manager = new StandardManager();
        manager.setContext(context);
        manager.setPersistAuthentication(true);
        StandardSession original = (StandardSession) manager.createSession("session-one");
        original.setMaxInactiveInterval(600);
        original.setAuthType("BASIC");
        original.setPrincipal(new GenericPrincipal("reader", List.of("catalog")));
        original.setAttribute("cart", "book");
        original.setAttribute("quantity", 3);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            original.writeObjectData(output);
        }
        StandardSession restored = new StandardSession(manager);
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored.readObjectData(input);
        }
        assertThat(restored.getId()).isEqualTo("session-one");
        assertThat(restored.getCreationTime()).isEqualTo(original.getCreationTime());
        assertThat(restored.getMaxInactiveInterval()).isEqualTo(600);
        assertThat(restored.getAttribute("cart")).isEqualTo("book");
        assertThat(restored.getAttribute("quantity")).isEqualTo(3);
        assertThat(restored.getAuthType()).isEqualTo("BASIC");
        assertThat(restored.getPrincipal().getName()).isEqualTo("reader");
        assertThat(((GenericPrincipal) restored.getPrincipal()).hasRole("catalog")).isTrue();
        assertThat(restored.isValid()).isTrue();
    }
}
