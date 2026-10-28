/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.annotation.AnnotationValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotationValueInnerForAnnotationDescriptionTest {
    @Test
    void loadsNestedAnnotationValue() throws Exception {
        AnnotationDescription nested = AnnotationDescription.Builder.ofType(TestFixtures.Label.class)
                .define("value", "selected").build();
        AnnotationValue<AnnotationDescription, TestFixtures.Label> value =
                new AnnotationValue.ForAnnotationDescription<>(nested);
        assertEquals("selected", value.load(getClass().getClassLoader()).resolve().value());
        assertEquals("selected", value.resolve().getValue(TestFixtures.property(TestFixtures.Label.class, "value"))
                .resolve(String.class));
    }
}
