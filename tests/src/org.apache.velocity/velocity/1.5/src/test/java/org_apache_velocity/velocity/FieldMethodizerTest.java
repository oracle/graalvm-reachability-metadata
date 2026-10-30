/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.FieldMethodizer;
import org.apache.velocity.runtime.RuntimeConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FieldMethodizerTest {
    @Test
    void exposesPublicConstantsAsTemplateProperties() throws Exception {
        FieldMethodizer constants = new FieldMethodizer(RuntimeConstants.class.getName());
        assertEquals(RuntimeConstants.RESOURCE_LOADER, constants.get("RESOURCE_LOADER"));
        VelocityContext context = new VelocityContext();
        context.put("constants", constants);
        assertEquals("resource.loader|input.encoding", VelocityTestSupport.render(context,
                "$constants.RESOURCE_LOADER|$constants.INPUT_ENCODING"));
    }
}
