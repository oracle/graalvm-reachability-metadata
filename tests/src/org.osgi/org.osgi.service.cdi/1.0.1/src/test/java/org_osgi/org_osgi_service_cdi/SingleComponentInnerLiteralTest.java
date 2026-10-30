/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_cdi;

import org.junit.jupiter.api.Test;
import org.osgi.service.cdi.annotations.ComponentScoped;
import org.osgi.service.cdi.annotations.SingleComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SingleComponentInnerLiteralTest {
    @Test
    void representsTheSingleComponentStereotype() {
        SingleComponent stereotype = SingleComponent.Literal.INSTANCE;
        SingleComponent equivalent = new SingleComponent.Literal();

        assertEquals(SingleComponent.class, stereotype.annotationType());
        assertEquals(equivalent, stereotype);
        assertEquals(stereotype, equivalent);
        assertEquals(0, stereotype.hashCode());
        assertNotEquals(ComponentScoped.Literal.INSTANCE, stereotype);
        assertTrue(stereotype.toString().contains(SingleComponent.class.getName()));
    }
}
