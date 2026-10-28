/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypeDescriptionInnerGenericInnerAnnotationReaderInnerDispatcherInnerForJava8CapableVmInnerAnnotatedSuperClassTest {
    @Test
    void readsSuperclassTypeAnnotation() {
        assertTrue(TestFixtures.sample().getSuperClass().asErasure().represents(TestFixtures.Parent.class));
        assertEquals("super", TestFixtures.mark(TestFixtures.sample().getSuperClass()));
    }
}
