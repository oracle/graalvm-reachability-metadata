/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_osgi.org_osgi_service_cdi;

import org.junit.jupiter.api.Test;
import org.osgi.service.cdi.annotations.Reference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReferenceInnerLiteralTest {
    @Test
    void describesServiceTypeAndTargetFilter() {
        Reference reference = Reference.Literal.of(Runnable.class, "(role=worker)");
        Reference equivalent = Reference.Literal.of(Runnable.class, "(role=worker)");

        assertEquals(Runnable.class, reference.value());
        assertEquals("(role=worker)", reference.target());
        assertEquals(Reference.class, reference.annotationType());
        assertEquals(equivalent, reference);
        assertEquals(reference, equivalent);
        assertEquals(equivalent.hashCode(), reference.hashCode());
        assertNotEquals(Reference.Literal.of(AutoCloseable.class, "(role=worker)"), reference);
        assertNotEquals(Reference.Literal.of(Runnable.class, "(role=reader)"), reference);
        assertTrue(reference.toString().contains("(role=worker)"));
    }

    @Test
    void describesAnyServiceWithNoTargetRestriction() {
        Reference reference = Reference.Literal.of(Reference.Any.class, "");

        assertEquals(Reference.Any.class, reference.value());
        assertEquals("", reference.target());
        assertEquals(Reference.Literal.of(Reference.Any.class, ""), reference);
    }
}
