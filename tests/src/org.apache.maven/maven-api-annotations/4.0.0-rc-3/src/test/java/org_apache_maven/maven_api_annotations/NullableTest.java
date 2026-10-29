/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_annotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Method;

import org.apache.maven.api.annotations.Nonnull;
import org.apache.maven.api.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class NullableTest {
    @Test
    // Checkstyle: allow direct annotation access
    void discoversNullableReturnValue() throws NoSuchMethodException {
        Method lookupAnnotationAccess = PropertyLookup.class.getMethod("find", String.class);
        Nullable annotation = annotation(lookupAnnotationAccess, Nullable.class);

        assertNotNull(annotation);
        assertEquals(Nullable.class, annotation.annotationType());
        assertTrue(lookupAnnotationAccess.isAnnotationPresent(Nullable.class));
    }
    // Checkstyle: disallow direct annotation access

    @Test
    void discoversNullableParameter() throws NoSuchMethodException {
        Method lookup = PropertyLookup.class.getMethod("find", String.class);
        Nullable parameterAnnotation = annotation(lookup.getParameters()[0], Nullable.class);
        Nullable returnAnnotation = annotation(lookup, Nullable.class);

        assertNotNull(parameterAnnotation);
        assertEquals(returnAnnotation, parameterAnnotation);
        assertEquals(returnAnnotation.hashCode(), parameterAnnotation.hashCode());
    }

    @Test
    void distinguishesNonNullableReturnValue() throws NoSuchMethodException {
        Method lookup = PropertyLookup.class.getMethod("name");

        assertNull(annotation(lookup, Nullable.class));
    }

    // Annotation lookup is the public API for this annotation-only library.
    // Checkstyle: allow direct annotation access
    private static <A extends Annotation> A annotation(AnnotatedElement element, Class<A> annotationType) {
        AnnotatedElement elementAnnotationAccess = element;
        return elementAnnotationAccess.getAnnotation(annotationType);
    }
    // Checkstyle: disallow direct annotation access

    public interface PropertyLookup {
        @Nullable
        String find(@Nullable String key);

        @Nonnull
        String name();
    }
}
