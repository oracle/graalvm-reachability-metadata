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
import java.util.Map;

import jakarta.faces.component.UIPanel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UIComponentBaseInnerAttributesMapTest {
    @Test
    void attributesReadAndWriteComponentBeanProperties() {
        try (TestFacesContext context = new TestFacesContext()) {
            UIPanel panel = new UIPanel();
            panel.setId("before");
            Map<String, Object> attributes = panel.getAttributes();
            assertEquals("before", attributes.get("id"));
            assertEquals("before", attributes.put("id", "after"));
            assertEquals("after", panel.getId());
            assertEquals(Boolean.TRUE, attributes.put("rendered", Boolean.FALSE));
            assertFalse(panel.isRendered());
            assertEquals(Boolean.FALSE, attributes.get("rendered"));
        }
    }

    @Test
    void serializesAttributeMapWithComponentState() throws Exception {
        try (TestFacesContext context = new TestFacesContext()) {
            UIPanel panel = new UIPanel();
            panel.setId("savedPanel");
            panel.getAttributes().put("theme", "dark");
            panel.getAttributes().put("columns", 3);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
                output.writeObject(panel.getAttributes());
            }
            try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
                Map<?, ?> restored = (Map<?, ?>) input.readObject();
                assertNotSame(panel.getAttributes(), restored);
                assertEquals("dark", restored.get("theme"));
                assertEquals(3, restored.get("columns"));
                assertTrue(restored.containsKey("theme"));
            }
        }
    }
}
