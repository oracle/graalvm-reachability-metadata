/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerDelegatorInnerChainedTest {
    @Test
    void followsNestedAnnotatedGenericStructure() {
        assertEquals("deep", TestFixtures.mark(TestFixtures.field("nested").getTypeArguments().getOnly()
                .getTypeArguments().getOnly().getComponentType()));
    }
}
