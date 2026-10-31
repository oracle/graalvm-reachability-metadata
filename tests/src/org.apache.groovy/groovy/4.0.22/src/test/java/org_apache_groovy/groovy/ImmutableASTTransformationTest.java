/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_groovy.groovy;

import org.codehaus.groovy.transform.ImmutableASTTransformation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ImmutableASTTransformationTest {
    @Test
    void createsReadOnlyCollectionsForImmutableProperties() {
        List<String> values = new ArrayList<>(List.of("first", "second"));
        Collection<?> legacy = (Collection<?>) ImmutableASTTransformation.checkImmutable(Envelope.class, "entries", values);
        Collection<?> configured = (Collection<?>) ImmutableASTTransformation.checkImmutable(
                Envelope.class, "entries", values, List.of(), List.of());
        assertEquals(values, List.copyOf(legacy));
        assertEquals(values, List.copyOf(configured));
        assertThrows(UnsupportedOperationException.class, legacy::clear);
        assertThrows(UnsupportedOperationException.class, configured::clear);
    }

    public static class Envelope {
        public List<String> entries;
    }
}
