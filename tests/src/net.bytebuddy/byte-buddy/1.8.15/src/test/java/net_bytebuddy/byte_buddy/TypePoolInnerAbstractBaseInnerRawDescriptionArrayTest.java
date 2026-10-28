/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import net.bytebuddy.build.HashCodeAndEqualsPlugin;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.enumeration.EnumerationDescription;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TypePoolInnerAbstractBaseInnerRawDescriptionArrayTest {
    @Test
    @SuppressWarnings("annotationAccess")
    void resolvesParsedAnnotationTargetArray() throws Exception {
        AnnotationDescription target = TestFixtures.libraryPool().describe(HashCodeAndEqualsPlugin.Enhance.class.getName())
                .resolve().getDeclaredAnnotations().ofType(Target.class);
        AnnotationValue<?, ?> value = target.getValue(TestFixtures.property(Target.class, "value"));
        EnumerationDescription[] descriptions = value.resolve(EnumerationDescription[].class);
        assertEquals(1, descriptions.length);
        assertEquals("TYPE", descriptions[0].getValue());
        assertArrayEquals(new ElementType[]{ElementType.TYPE}, value.load(getClass().getClassLoader()).resolve(ElementType[].class));
    }
}
