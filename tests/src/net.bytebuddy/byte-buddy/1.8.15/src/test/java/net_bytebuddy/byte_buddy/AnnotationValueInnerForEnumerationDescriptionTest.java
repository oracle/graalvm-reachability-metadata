/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.enumeration.EnumerationDescription;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AnnotationValueInnerForEnumerationDescriptionTest {
    @Test
    void resolvesEnumConstantFromDescription() throws Exception {
        AnnotationValue<EnumerationDescription, TestFixtures.Mode> value =
                AnnotationValue.ForEnumerationDescription.of(
                        new EnumerationDescription.ForLoadedEnumeration(TestFixtures.Mode.WRITE));
        AnnotationValue.Loaded<TestFixtures.Mode> loaded = value.load(getClass().getClassLoader());
        assertEquals(TestFixtures.Mode.WRITE, loaded.resolve());
        assertTrue(loaded.represents(TestFixtures.Mode.WRITE));
        assertEquals("WRITE", value.resolve().getValue());
    }
}
