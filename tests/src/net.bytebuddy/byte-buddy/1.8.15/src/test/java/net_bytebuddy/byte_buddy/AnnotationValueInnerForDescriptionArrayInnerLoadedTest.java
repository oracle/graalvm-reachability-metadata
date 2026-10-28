/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.annotation.AnnotationValue;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class AnnotationValueInnerForDescriptionArrayInnerLoadedTest {
    @Test
    void returnsIndependentArraysForEnumValues() throws Exception {
        AnnotationValue.Loaded<?> value = TestFixtures.options()
                .getValue(TestFixtures.property(TestFixtures.Options.class, "modes")).load(getClass().getClassLoader());
        TestFixtures.Mode[] first = value.resolve(TestFixtures.Mode[].class);
        assertArrayEquals(new TestFixtures.Mode[]{TestFixtures.Mode.READ, TestFixtures.Mode.WRITE}, first);
        first[0] = TestFixtures.Mode.WRITE;
        assertArrayEquals(new TestFixtures.Mode[]{TestFixtures.Mode.READ, TestFixtures.Mode.WRITE},
                value.resolve(TestFixtures.Mode[].class));
    }
}
