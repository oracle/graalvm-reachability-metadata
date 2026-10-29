/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_glassfish_gmbal.gmbal_api_only;

import org.glassfish.gmbal.util.GenericConstructor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GenericConstructorTest {
    @Test
    void createsAnObjectWithTheDefaultConstructor() {
        GenericConstructor<StringBuilder> constructor = new GenericConstructor<>(
                StringBuilder.class, "java.lang.StringBuilder");

        StringBuilder builder = constructor.create();

        assertNotNull(builder);
        assertEquals(0, builder.length());
        builder.append("managed").append('-').append("object");
        assertEquals("managed-object", builder.toString());
    }
}
