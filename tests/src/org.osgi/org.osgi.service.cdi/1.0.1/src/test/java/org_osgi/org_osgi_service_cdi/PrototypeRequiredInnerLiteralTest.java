/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_cdi;

import org.junit.jupiter.api.Test;
import org.osgi.service.cdi.annotations.PrototypeRequired;
import org.osgi.service.cdi.annotations.Reluctant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PrototypeRequiredInnerLiteralTest {
    @Test
    void representsThePrototypeRequirementQualifier() {
        PrototypeRequired qualifier = PrototypeRequired.Literal.INSTANCE;
        PrototypeRequired equivalent = new PrototypeRequired.Literal();

        assertEquals(PrototypeRequired.class, qualifier.annotationType());
        assertEquals(equivalent, qualifier);
        assertEquals(qualifier, equivalent);
        assertEquals(0, qualifier.hashCode());
        assertNotEquals(Reluctant.Literal.INSTANCE, qualifier);
        assertTrue(qualifier.toString().contains(PrototypeRequired.class.getName()));
    }
}
