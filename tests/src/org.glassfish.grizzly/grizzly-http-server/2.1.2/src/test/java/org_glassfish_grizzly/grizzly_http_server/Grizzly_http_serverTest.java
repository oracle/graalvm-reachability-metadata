/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_grizzly.grizzly_http_server;

import org.glassfish.grizzly.http.server.Session;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Grizzly_http_serverTest {
    @Test
    void removesSessionAttributesWithoutAffectingOtherAttributes() {
        Session session = new Session("shopping-session");
        session.setAttribute("cart", "book");
        session.setAttribute("currency", "EUR");

        assertEquals("book", session.getAttribute("cart"));
        assertEquals("book", session.removeAttribute("cart"));
        assertNull(session.getAttribute("cart"));
        assertEquals("EUR", session.getAttribute("currency"));
        assertEquals(1, session.attributes().size());
        assertEquals("EUR", session.removeAttribute("currency"));
        assertTrue(session.attributes().isEmpty());
    }
}
