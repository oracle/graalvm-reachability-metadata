/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import org.eclipse.osgi.util.NLS;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NLSTest {
    public static class Messages extends NLS {
        public static String greeting;
        public static String optional;
    }

    @Test
    public void initializesMessageFieldsAndMarksMissingKeys() {
        NLS.initializeMessages("org_eclipse.osgi.messages", Messages.class);
        assertEquals("Welcome, Ada!", NLS.bind(Messages.greeting, "Ada"));
        assertTrue(Messages.optional.startsWith("NLS missing message: optional in: "));
        assertTrue(Messages.optional.endsWith("org_eclipse.osgi.messages"));
    }
}
