/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.annotation.AnnotationValue;
import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TypePoolInnerAbstractBaseInnerRawTypeValueTest {
    @Test
    void loadsClassValuedAnnotationDefault() throws Exception {
        AnnotationValue<?, ?> value = TestFixtures.libraryPool().describe(Advice.OnMethodEnter.class.getName()).resolve()
                .getDeclaredMethods().filter(named("suppress")).getOnly().getDefaultValue();
        Class<?> loaded = value.load(getClass().getClassLoader()).resolve(Class.class);
        assertEquals(value.resolve(TypeDescription.class).getName(), loaded.getName());
        assertTrue(Throwable.class.isAssignableFrom(loaded));
    }
}
