/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.apache.commons.dbcp2.datasources;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class CharArrayTest {

    @Test
    public void testAsString() {
        assertEquals("foo", new CharArray("foo").asString());
    }

    @Test
    public void testEquals() {
        assertEquals(new CharArray("foo"), new CharArray("foo"));
        assertNotEquals(new CharArray("foo"), new CharArray("bar"));
    }

    @Test
    public void testGet() {
        assertArrayEquals("foo".toCharArray(), new CharArray("foo").get());
    }

    @Test
    public void testHashCode() {
        assertEquals(new CharArray("foo").hashCode(), new CharArray("foo").hashCode());
        assertNotEquals(new CharArray("foo").hashCode(), new CharArray("bar").hashCode());
    }

    @Test
    public void testToString() {
        assertFalse(new CharArray("foo").toString().contains("foo"));
    }
}
