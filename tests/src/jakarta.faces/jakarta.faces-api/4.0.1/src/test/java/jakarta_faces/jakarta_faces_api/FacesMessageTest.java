/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_faces.jakarta_faces_api;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.List;

import jakarta.faces.application.FacesMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FacesMessageTest {
    @Test
    void serializesMessagesWithEverySeverity() throws Exception {
        for (FacesMessage.Severity severity : List.of(FacesMessage.SEVERITY_INFO, FacesMessage.SEVERITY_WARN,
                FacesMessage.SEVERITY_ERROR, FacesMessage.SEVERITY_FATAL)) {
            FacesMessage original = new FacesMessage(severity, "Invalid postal code", "Enter five digits");
            original.rendered();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(original);
            }
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                FacesMessage restored = (FacesMessage) input.readObject();
                assertEquals("Invalid postal code", restored.getSummary());
                assertEquals("Enter five digits", restored.getDetail());
                assertSame(severity, restored.getSeverity());
                assertTrue(restored.isRendered());
            }
        }
    }

    @Test
    void serializesUnrenderedMessageWithoutDetail() throws Exception {
        FacesMessage message = new FacesMessage("Saved");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(message);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            FacesMessage restored = (FacesMessage) input.readObject();
            assertEquals("Saved", restored.getDetail());
            assertFalse(restored.isRendered());
        }
    }
}
