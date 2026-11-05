/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.classgen.Verifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class VerifierTest {
    @Test
    void readsACompiledClassTimestampAndRecognizesClassesWithoutOne() {
        assertEquals(123456789L, Verifier.getTimestamp(Timestamped.class));
        assertEquals(Long.MAX_VALUE, Verifier.getTimestamp(Ordinary.class));
    }

    public static class Timestamped {
        public static final long __timeStamp__239_neverHappen123456789 = 0;
    }

    public static class Ordinary {
        public String value;
    }
}
