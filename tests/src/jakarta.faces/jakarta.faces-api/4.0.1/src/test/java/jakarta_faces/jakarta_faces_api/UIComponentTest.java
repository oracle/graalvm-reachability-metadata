/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_faces.jakarta_faces_api;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class UIComponentTest {
    @Test
    void componentResolvesItsOwnResourceBundleAsReadOnlyMap() {
        try (TestFacesContext context = new TestFacesContext()) {
            LocalizedPanel panel = new LocalizedPanel();
            Map<String, String> bundle = panel.getResourceBundleMap();
            assertEquals("Account settings", bundle.get("title"));
            assertEquals("Choose your preferences", bundle.get("hint"));
            assertEquals("???missing???", bundle.get("missing"));
            assertTrue(bundle.containsKey("title"));
            assertEquals(2, bundle.size());
            assertThrows(UnsupportedOperationException.class, () -> bundle.put("title", "Other"));
        }
    }
}
