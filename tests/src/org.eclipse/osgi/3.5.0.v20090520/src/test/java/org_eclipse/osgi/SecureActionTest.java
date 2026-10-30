/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import org.eclipse.osgi.framework.util.SecureAction;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class SecureActionTest {
    @Test
    public void looksUpExistingClassesByName() throws Exception {
        SecureAction action = (SecureAction) SecureAction.createSecureAction().run();
        assertSame(Version.class, action.forName("org.osgi.framework.Version"));
        assertSame(String.class, action.loadSystemClass("java.lang.String"));
        assertEquals("2.4.0", new Version("2.4.0").toString());
    }
}
