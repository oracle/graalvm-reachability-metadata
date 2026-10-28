/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerForWildcardUpperBoundTypeTest {
    @Test
    void readsAnnotatedExtendsBound() {
        TypeDescription.Generic bound = TestFixtures.field("upper").getTypeArguments().getOnly().getUpperBounds().getOnly();
        assertTrue(bound.asErasure().represents(Number.class));
        assertEquals("upper", TestFixtures.mark(bound));
    }
}
