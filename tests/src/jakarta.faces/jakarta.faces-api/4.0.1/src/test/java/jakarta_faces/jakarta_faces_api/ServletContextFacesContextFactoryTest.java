/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package jakarta_faces.jakarta_faces_api;

import jakarta.faces.context.FacesContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ServletContextFacesContextFactoryTest {
    @Test
    void currentContextLookupOutsideAndInsideARequest() {
        assertNull(FacesContext.getCurrentInstance());
        try (TestFacesContext context = new TestFacesContext()) {
            assertSame(context, FacesContext.getCurrentInstance());
        }
        assertNull(FacesContext.getCurrentInstance());
    }
}
