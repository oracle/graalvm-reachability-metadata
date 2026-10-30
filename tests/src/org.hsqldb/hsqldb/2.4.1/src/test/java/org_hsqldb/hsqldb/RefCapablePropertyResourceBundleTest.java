/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.util.Locale;

import org.hsqldb.lib.RefCapablePropertyResourceBundle;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RefCapablePropertyResourceBundleTest {
    @Test
    void loadsInlinePropertyValues() {
        RefCapablePropertyResourceBundle bundle = RefCapablePropertyResourceBundle.getBundle(
                "org.hsqldb.resources.sql-state-messages", Locale.ROOT, getClass().getClassLoader());
        assertEquals("00000 successful completion", bundle.getString("0000"));
    }
}
