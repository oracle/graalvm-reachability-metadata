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

public class NLSInnerMessagesPropertiesTest {
    public static class Messages extends NLS {
        public static String title;
        public static String detail;
    }

    @Test
    public void assignsPropertyValuesToMessageFields() {
        NLS.initializeMessages("org_eclipse.osgi.catalog", Messages.class);
        assertEquals("Catalog", Messages.title);
        assertEquals("Loaded 3 entries", NLS.bind(Messages.detail, 3));
    }
}
