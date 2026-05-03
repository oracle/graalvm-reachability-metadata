/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_modules.jboss_modules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.NoSuchElementException;

import org.jboss.modules.Version;
import org.junit.jupiter.api.Test;

public class Jboss_modulesTest {
    @Test
    void parsesVersionIntoNumericAlphaAndSeparatorTokens() {
        Version version = Version.parse("10.20-Final_3");
        Version.Iterator iterator = version.iterator();

        assertTrue(iterator.hasNext());
        iterator.next();
        assertTrue(iterator.isNumberPart());
        assertFalse(iterator.isSeparator());
        assertEquals("10", iterator.getNumberPartAsString());
        assertEquals(10, iterator.getNumberPartAsInt());
        assertEquals(10L, iterator.getNumberPartAsLong());
        assertEquals(BigInteger.TEN, iterator.getNumberPartAsBigInteger());

        assertTrue(iterator.hasNext());
        iterator.next();
        assertTrue(iterator.isNonEmptySeparator());
        assertEquals('.', iterator.getSeparatorCodePoint());
        assertEquals(1, iterator.length());

        assertTrue(iterator.hasNext());
        iterator.next();
        assertTrue(iterator.isNumberPart());
        assertEquals("20", iterator.getNumberPartAsString());

        assertTrue(iterator.hasNext());
        iterator.next();
        assertTrue(iterator.isNonEmptySeparator());
        assertEquals('-', iterator.getSeparatorCodePoint());

        assertTrue(iterator.hasNext());
        iterator.next();
        assertTrue(iterator.isAlphaPart());
        assertTrue(iterator.isPart());
        assertEquals("Final", iterator.getAlphaPart());

        assertTrue(iterator.hasNext());
        iterator.next();
        assertTrue(iterator.isNonEmptySeparator());
        assertEquals('_', iterator.getSeparatorCodePoint());

        assertTrue(iterator.hasNext());
        iterator.next();
        assertTrue(iterator.isNumberPart());
        assertEquals("3", iterator.getNumberPartAsString());
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void exposesEmptySeparatorsBetweenAdjacentNumberAndAlphaParts() {
        Version.Iterator iterator = Version.parse("1Final").iterator();

        iterator.next();
        assertTrue(iterator.isNumberPart());
        assertEquals("1", iterator.getNumberPartAsString());

        iterator.next();
        assertTrue(iterator.isEmptySeparator());
        assertTrue(iterator.isSeparator());
        assertEquals(0, iterator.length());

        iterator.next();
        assertTrue(iterator.isAlphaPart());
        assertEquals("Final", iterator.getAlphaPart());
        assertFalse(iterator.hasNext());
    }

    @Test
    void comparesVersionsByParsedParts() {
        Version release = Version.parse("1.10.Final");
        Version earlierPatch = Version.parse("1.2.Final");
        Version sameRelease = Version.parse("1.10.Final");
        Version servicePack = Version.parse("1.10.SP1");

        assertTrue(release.compareTo(earlierPatch) > 0);
        assertTrue(earlierPatch.compareTo(release) < 0);
        assertEquals(0, release.compareTo(sameRelease));
        assertEquals(release, sameRelease);
        assertEquals(release.hashCode(), sameRelease.hashCode());
        assertEquals("1.10.Final", release.toString());
        assertTrue(servicePack.compareTo(release) > 0);
    }

    @Test
    void rejectsInvalidVersionStrings() {
        assertThrows(IllegalArgumentException.class, () -> Version.parse(""));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1."));
        assertThrows(IllegalArgumentException.class, () -> Version.parse("1#2"));
    }
}
