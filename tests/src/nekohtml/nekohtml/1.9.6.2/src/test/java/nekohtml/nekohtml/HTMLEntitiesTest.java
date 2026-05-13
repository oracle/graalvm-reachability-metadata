/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package nekohtml.nekohtml;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.cyberneko.html.HTMLEntities;
import org.junit.jupiter.api.Test;

public class HTMLEntitiesTest {
    @Test
    void resolvesNamedEntitiesFromBundledResources() {
        assertEquals('&', HTMLEntities.get("amp"));
        assertEquals('<', HTMLEntities.get("lt"));
        assertEquals('>', HTMLEntities.get("gt"));
        assertEquals('"', HTMLEntities.get("quot"));
        assertEquals(-1, HTMLEntities.get("notAnEntity"));
    }

    @Test
    void resolvesCodePointsToEntityNames() {
        assertEquals("amp", HTMLEntities.get('&'));
        assertEquals("lt", HTMLEntities.get('<'));
        assertEquals("gt", HTMLEntities.get('>'));
        assertEquals("quot", HTMLEntities.get('"'));
    }
}
