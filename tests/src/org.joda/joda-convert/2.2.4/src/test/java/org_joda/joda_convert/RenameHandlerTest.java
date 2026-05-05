/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_joda.joda_convert;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.joda.convert.RenameHandler;
import org.junit.jupiter.api.Test;

public class RenameHandlerTest {
    @Test
    public void loadsRenameConfigurationResourcesFromClasspath() throws Exception {
        RenameHandler handler = RenameHandler.create(true);

        assertSame(RenameHandlerTest.class, handler.lookupType("example.LegacyName"));
        assertEquals(Status.NEW_STATUS, handler.lookupEnum(Status.class, "OLD_STATUS"));
    }

    public enum Status {
        NEW_STATUS
    }
}
