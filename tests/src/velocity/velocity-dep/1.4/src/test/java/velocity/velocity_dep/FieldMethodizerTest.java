/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity_dep;

import org.apache.velocity.app.FieldMethodizer;
import org.apache.velocity.runtime.RuntimeConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FieldMethodizerTest {
    @Test
    void exposesPublicStaticFieldsFromClassName() throws Exception {
        FieldMethodizer methodizer = new FieldMethodizer();

        methodizer.addObject(RuntimeConstants.class.getName());

        assertEquals(RuntimeConstants.INPUT_ENCODING, methodizer.get("INPUT_ENCODING"));
        assertEquals(RuntimeConstants.RESOURCE_LOADER, methodizer.get("RESOURCE_LOADER"));
        assertNull(methodizer.get("missingField"));
    }
}
