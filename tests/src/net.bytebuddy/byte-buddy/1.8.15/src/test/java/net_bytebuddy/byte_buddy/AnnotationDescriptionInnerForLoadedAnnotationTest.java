/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_bytebuddy.byte_buddy;

import net.bytebuddy.build.HashCodeAndEqualsPlugin;
import net.bytebuddy.description.annotation.AnnotationDescription;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import org.junit.jupiter.api.Test;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class AnnotationDescriptionInnerForLoadedAnnotationTest {
    @HashCodeAndEqualsPlugin.Enhance(includeSyntheticFields = true, permitSubclassEquality = false)
    public static class Configured {
    }

    @Test
    @SuppressWarnings("annotationAccess")
    void readsLoadedAnnotationUsingParsedPropertyDescriptions() {
        AnnotationDescription description = TypeDescription.ForLoadedType.of(Configured.class)
                .getDeclaredAnnotations().ofType(HashCodeAndEqualsPlugin.Enhance.class);
        TypeDescription annotationType = TestFixtures.libraryPool()
                .describe(HashCodeAndEqualsPlugin.Enhance.class.getName()).resolve();
        MethodDescription.InDefinedShape included = annotationType.getDeclaredMethods()
                .filter(named("includeSyntheticFields")).getOnly();
        MethodDescription.InDefinedShape subclasses = annotationType.getDeclaredMethods()
                .filter(named("permitSubclassEquality")).getOnly();
        assertEquals(Boolean.TRUE, description.getValue(included).resolve(Boolean.class));
        assertEquals(Boolean.FALSE, description.getValue(subclasses).resolve(Boolean.class));
    }

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
