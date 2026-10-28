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

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerForOwnerTypeTest {
    @Test
    void readsAnnotatedParameterizedOwner() {
        TypeDescription.Generic owner = TestFixtures.field("member").getOwnerType();
        assertTrue(owner.asErasure().represents(TestFixtures.Owner.class));
        assertTrue(owner.getTypeArguments().getOnly().asErasure().represents(String.class));
        assertEquals("owner", TestFixtures.mark(owner));
    }
}
