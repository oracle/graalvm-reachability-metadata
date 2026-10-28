/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class AnnotationDescriptionInnerAnnotationInvocationHandlerTest {
    @Test
    @SuppressWarnings("annotationAccess")
    void buildsAnnotationWithValueEquality() throws Exception {
        TestFixtures.Label actual = TypeDescription.ForLoadedType.of(TestFixtures.Annotated.class)
                .getDeclaredAnnotations().ofType(TestFixtures.Label.class).load();
        TestFixtures.Label created = AnnotationDescription.Builder.ofType(TestFixtures.Label.class)
                .define("value", "catalog").build().prepare(TestFixtures.Label.class).load();
        TestFixtures.Label different = AnnotationDescription.Builder.ofType(TestFixtures.Label.class)
                .define("value", "other").build().prepare(TestFixtures.Label.class).load();
        assertEquals("catalog", created.value());
        assertEquals(actual, created);
        assertEquals(created, actual);
        assertEquals(actual.hashCode(), created.hashCode());
        assertNotEquals(created, different);
        assertEquals(TestFixtures.Label.class, created.annotationType());
    }
}
