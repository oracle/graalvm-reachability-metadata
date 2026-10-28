/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bind.annotation.Super;
import org.junit.jupiter.api.Test;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypePoolInnerAbstractBaseInnerRawDescriptionArrayInnerLoadedTest {
    @Test
    void loadsEmptyClassArrayDefaultWithCorrectComponentType() throws Exception {
        AnnotationValue<?, ?> value = TestFixtures.libraryPool().describe(Super.class.getName()).resolve()
                .getDeclaredMethods().filter(named("constructorParameters")).getOnly().getDefaultValue();
        assertArrayEquals(new TypeDescription[0], value.resolve(TypeDescription[].class));
        AnnotationValue.Loaded<?> loaded = value.load(getClass().getClassLoader());
        assertTrue(loaded.getState().isResolved());
        assertArrayEquals(new Class<?>[0], loaded.resolve(Class[].class));
    }
}
