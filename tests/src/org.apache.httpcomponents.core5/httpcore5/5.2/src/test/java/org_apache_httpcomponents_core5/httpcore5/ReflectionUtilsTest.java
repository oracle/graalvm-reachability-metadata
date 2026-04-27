/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_httpcomponents_core5.httpcore5;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.hc.core5.net.URIBuilder;
import org.apache.hc.core5.util.ReflectionUtils;
import org.junit.jupiter.api.Test;

public class ReflectionUtilsTest {

    @Test
    void callsPublicSetterAndGetterByPropertyName() {
        final URIBuilder uriBuilder = new URIBuilder();

        assertNull(ReflectionUtils.callGetter(uriBuilder, "Host", String.class));

        ReflectionUtils.callSetter(uriBuilder, "Host", String.class, "example.org");

        assertEquals("example.org", ReflectionUtils.callGetter(uriBuilder, "Host", String.class));
    }
}
