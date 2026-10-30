/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse.osgi;

import java.util.Dictionary;

import org.eclipse.osgi.framework.internal.core.FilterImpl;
import org.junit.jupiter.api.Test;
import org.osgi.framework.Version;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FilterImplTest {
    @Test
    public void comparesTypedServiceProperties() throws Exception {
        Dictionary<String, Object> properties = new MapDictionary<>();
        properties.put("revision", new Version("2.4.0"));
        properties.put("label", new FilterValues.Label("stable"));

        assertTrue(FilterImpl.newInstance("(&(revision>=2.0.0)(revision<=3.0.0)(label=stable))").match(properties));
        assertTrue(FilterImpl.newInstance("(revision~=2.4.0)").match(properties));
        assertFalse(FilterImpl.newInstance("(revision>=3.0.0)").match(properties));
        assertFalse(FilterImpl.newInstance("(label=preview)").match(properties));
    }
}
