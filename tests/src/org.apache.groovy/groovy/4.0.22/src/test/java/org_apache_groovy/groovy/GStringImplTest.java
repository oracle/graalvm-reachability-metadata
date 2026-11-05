/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import groovy.transform.Pure;
import org.codehaus.groovy.runtime.GStringImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GStringImplTest {
    @Test
    void interpolatesPureAndMutableValues() {
        GStringImpl immutable = new GStringImpl(new Object[]{new Code()}, new String[]{"code=", "."});
        assertEquals("code=AB.", immutable.toString());
        assertEquals("code=AB.", immutable.toString());
        StringBuilder text = new StringBuilder("first");
        GStringImpl mutable = new GStringImpl(new Object[]{text}, new String[]{"", ""});
        assertEquals("first", mutable.toString());
        text.append(" second");
        assertEquals("first second", mutable.toString());
    }

    public static class Code {
        @Pure
        @Override
        public String toString() {
            return "AB";
        }
    }
}
