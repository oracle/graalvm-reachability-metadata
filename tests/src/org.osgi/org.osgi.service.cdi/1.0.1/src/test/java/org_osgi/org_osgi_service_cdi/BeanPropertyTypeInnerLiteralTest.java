/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_cdi;

import org.junit.jupiter.api.Test;
import org.osgi.service.cdi.annotations.BeanPropertyType;
import org.osgi.service.cdi.annotations.ComponentProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeanPropertyTypeInnerLiteralTest {
    @Test
    void representsTheBeanPropertyTypeMarker() {
        BeanPropertyType marker = BeanPropertyType.Literal.INSTANCE;
        BeanPropertyType equivalent = new BeanPropertyType.Literal();

        assertEquals(BeanPropertyType.class, marker.annotationType());
        assertEquals(equivalent, marker);
        assertEquals(marker, equivalent);
        assertEquals(0, marker.hashCode());
        assertNotEquals(ComponentProperties.Literal.INSTANCE, marker);
        assertTrue(marker.toString().contains(BeanPropertyType.class.getName()));
    }
}
