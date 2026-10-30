/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import java.util.Hashtable;

import org.junit.jupiter.api.Test;
import org.osgi.framework.FrameworkUtil;
import org.osgi.framework.Version;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FrameworkUtilInnerFilterImplTest {
    @Test
    public void matchesComparableAndApplicationDefinedProperties() throws Exception {
        Hashtable<String, Object> properties = new Hashtable<>();
        properties.put("revision", new Version("2.4.0"));
        properties.put("label", new FilterValues.Label("release"));

        assertTrue(FrameworkUtil.createFilter("(&(revision>=2.0.0)(revision<=3.0.0)(label=release))").match(properties));
        assertTrue(FrameworkUtil.createFilter("(revision=2.4.0)").match(properties));
        assertFalse(FrameworkUtil.createFilter("(revision<=2.0.0)").match(properties));
        assertFalse(FrameworkUtil.createFilter("(label=development)").match(properties));
    }
}
