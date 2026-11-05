/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_cdi;

import org.junit.jupiter.api.Test;
import org.osgi.service.cdi.annotations.MinimumCardinality;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MinimumCardinalityInnerLiteralTest {
    @Test
    void describesRequiredNumberOfServiceReferences() {
        MinimumCardinality cardinality = MinimumCardinality.Literal.of(3);
        MinimumCardinality equivalent = MinimumCardinality.Literal.of(3);

        assertEquals(3, cardinality.value());
        assertEquals(MinimumCardinality.class, cardinality.annotationType());
        assertEquals(equivalent, cardinality);
        assertEquals(equivalent.hashCode(), cardinality.hashCode());
        assertNotEquals(MinimumCardinality.Literal.of(1), cardinality);
        assertTrue(cardinality.toString().contains("3"));
    }
}
