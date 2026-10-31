/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package it_unimi_dsi.fastutil;

import it.unimi.dsi.fastutil.objects.ReferenceLinkedOpenHashSet;
import it_unimi_dsi.fastutil.FastutilTestSupport.Key;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static it_unimi_dsi.fastutil.FastutilTestSupport.roundTrip;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ReferenceLinkedOpenHashSetTest {
    @Test
    void persistsMembershipAndRemainsMutable() throws IOException, ClassNotFoundException {
        int size = 5;
        ReferenceLinkedOpenHashSet<Key> original = new ReferenceLinkedOpenHashSet<>();
        for (int i = size - 1; i >= 0; i--) {
            assertTrue(original.add(Key.values()[i]));
        }
        assertTrue(original.add(null));

        ReferenceLinkedOpenHashSet<Key> copy = roundTrip(original);
        assertEquals(original.size(), copy.size());
        for (int i = 0; i < size; i++) {
            assertTrue(copy.contains(Key.values()[i]));
            assertFalse(copy.add(Key.values()[i]));
        }
        assertTrue(copy.contains(null));
        assertFalse(copy.add(null));
        Object[] expectedKeys = new Object[size + 1];
        for (int i = 0; i < size; i++) {
            expectedKeys[i] = Key.values()[size - i - 1];
        }
        assertArrayEquals(expectedKeys, copy.toArray());

        assertTrue(copy.add(Key.EXTRA));
        assertTrue(copy.contains(Key.EXTRA));
        assertTrue(copy.remove(Key.EXTRA));
        assertFalse(copy.contains(Key.EXTRA));
        assertEquals(original.size(), copy.size());
    }
}
