/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.method.ParameterDescription;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ParameterDescriptionInnerForLoadedParameterInnerDispatcherInnerForJava8CapableVmTest {
    @Test
    void readsParameterIdentityModifiersAndName() {
        ParameterDescription parameter = TestFixtures.method("transform").getParameters().getOnly();
        assertEquals(0, parameter.getIndex());
        assertEquals(0, parameter.getModifiers());
        assertTrue(parameter.getName().matches("input|arg0"));
        assertEquals("input".equals(parameter.getName()), parameter.isNamed());
        assertTrue(parameter.getType().asErasure().represents(String.class));
    }
}
