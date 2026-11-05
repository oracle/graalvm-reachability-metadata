/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hsqldb.hsqldb;

import java.util.Locale;
import java.util.ResourceBundle;

import org.hsqldb.resources.ResourceBundleHandler;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ResourceBundleHandlerTest {
    @Test
    void loadsSqlStateBundleWithAndWithoutAClassLoader() {
        String name = "org.hsqldb.resources.sql-state-messages";
        ResourceBundle standard = ResourceBundleHandler.getBundle(name, Locale.ROOT, null);
        ResourceBundle explicit = ResourceBundleHandler.getBundle(name, Locale.ROOT, getClass().getClassLoader());
        assertEquals("00000 successful completion", standard.getString("0000"));
        assertEquals(standard.getString("0000"), explicit.getString("0000"));
        int handle = ResourceBundleHandler.getBundleHandle("sql-state-messages", getClass().getClassLoader());
        assertTrue(ResourceBundleHandler.getString(handle, "0104").contains("23505"));
    }
}
