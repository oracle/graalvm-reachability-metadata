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
import static org.junit.jupiter.api.Assertions.assertNotSame;

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

    @Test
    void reusesTheConstructorToCreateIndependentObjectsThroughAnInterface() {
        GenericConstructor<CharSequence> constructor = new GenericConstructor<>(
                CharSequence.class, "java.lang.StringBuilder", String.class);

        CharSequence first = constructor.create("first");
        CharSequence second = constructor.create("second");

        assertNotNull(first);
        assertNotNull(second);
        assertNotSame(first, second);
        assertEquals("first", first.toString());
        assertEquals("second", second.toString());
        assertEquals(5, first.length());
        assertEquals('s', second.charAt(0));
        assertEquals("eco", second.subSequence(1, 4).toString());
    }
}
