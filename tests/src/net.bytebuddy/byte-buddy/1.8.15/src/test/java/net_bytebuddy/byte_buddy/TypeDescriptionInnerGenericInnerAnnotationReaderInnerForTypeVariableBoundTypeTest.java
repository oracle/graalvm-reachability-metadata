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

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerForTypeVariableBoundTypeTest {
    @Test
    void followsTypeVariableUseToItsAnnotatedBound() {
        TypeDescription.Generic variable = TestFixtures.field("variable");
        assertEquals("T", variable.getSymbol());
        assertTrue(variable.getUpperBounds().getOnly().asErasure().represents(Number.class));
        assertEquals("bound", TestFixtures.mark(variable.getUpperBounds().getOnly()));
    }
}
