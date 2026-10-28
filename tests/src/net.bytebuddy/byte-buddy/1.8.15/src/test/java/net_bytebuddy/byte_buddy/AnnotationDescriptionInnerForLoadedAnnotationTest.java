/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.annotation.AnnotationDescription;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotationDescriptionInnerForLoadedAnnotationTest {
    @Test
    void readsNestedAnnotationProperties() throws Exception {
        AnnotationDescription description = TestFixtures.options();
        AnnotationDescription nested = description.getValue(TestFixtures.property(TestFixtures.Options.class, "nested"))
                .resolve(AnnotationDescription.class);
        assertEquals("nested", nested.prepare(TestFixtures.Label.class).load().value());
        assertEquals(TestFixtures.Mode.READ, description.getValue(TestFixtures.property(TestFixtures.Options.class, "mode"))
                .load(getClass().getClassLoader()).resolve());
    }
}
