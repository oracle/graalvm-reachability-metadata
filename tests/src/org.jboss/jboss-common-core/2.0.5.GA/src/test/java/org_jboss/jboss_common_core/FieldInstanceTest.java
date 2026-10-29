/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import org.jboss.util.FieldInstance;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

public class FieldInstanceTest {
    @Test
    void readsAndUpdatesAnApplicationFieldByName() throws Exception {
        Settings settings = new Settings();
        FieldInstance field = new FieldInstance(settings, "destination");

        assertSame(settings, field.getInstance());
        assertEquals("incoming", field.get());
        field.set("processed");
        assertEquals("processed", settings.destination);
        settings.destination = "archived";
        assertEquals("archived", field.get());
    }

    public static class Settings {
        public String destination = "incoming";
    }
}
