/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss.jboss_common_core;

import org.jboss.util.property.FieldBoundPropertyListener;
import org.jboss.util.property.PropertyMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FieldBoundPropertyListenerTest {
    @Test
    void bindsExistingPropertyAndTracksChangesUntilRemoved() {
        Limits limits = new Limits();
        PropertyMap properties = new PropertyMap();
        properties.setProperty("worker.limit", "12");
        FieldBoundPropertyListener listener =
                new FieldBoundPropertyListener(limits, "maximum", "worker.limit");

        properties.addPropertyListener(listener);
        assertEquals(12, limits.maximum);
        properties.setProperty("worker.limit", "24");
        assertEquals(24, limits.maximum);
        properties.setProperty("other.limit", "99");
        assertEquals(24, limits.maximum);

        assertTrue(properties.removePropertyListener(listener));
        properties.setProperty("worker.limit", "48");
        assertEquals(24, limits.maximum);
    }

    @Test
    void bindsAPropertyAddedAfterRegistration() {
        Limits limits = new Limits();
        PropertyMap properties = new PropertyMap();
        properties.addPropertyListener(new FieldBoundPropertyListener(limits, "maximum"));
        assertEquals(0, limits.maximum);

        properties.setProperty("maximum", "7");
        assertEquals(7, limits.maximum);
    }

    public static class Limits {
        public int maximum;
    }
}
