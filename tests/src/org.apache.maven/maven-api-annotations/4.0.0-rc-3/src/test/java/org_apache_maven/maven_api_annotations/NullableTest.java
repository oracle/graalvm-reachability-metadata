/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_annotations;

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
    void discoversNullableReturnValue() throws NoSuchMethodException {
        Method lookup = PropertyLookup.class.getMethod("find", String.class);
        Nullable annotation = lookup.getAnnotation(Nullable.class);

        assertNotNull(annotation);
        assertEquals(Nullable.class, annotation.annotationType());
        assertTrue(lookup.isAnnotationPresent(Nullable.class));
    }

    @Test
    void discoversNullableParameter() throws NoSuchMethodException {
        Method lookup = PropertyLookup.class.getMethod("find", String.class);
        Nullable parameterAnnotation = lookup.getParameters()[0].getAnnotation(Nullable.class);
        Nullable returnAnnotation = lookup.getAnnotation(Nullable.class);

        assertNotNull(parameterAnnotation);
        assertEquals(returnAnnotation, parameterAnnotation);
        assertEquals(returnAnnotation.hashCode(), parameterAnnotation.hashCode());
    }

    @Test
    void distinguishesNonNullableReturnValue() throws NoSuchMethodException {
        Method lookup = PropertyLookup.class.getMethod("name");

        assertNull(lookup.getAnnotation(Nullable.class));
    }

    public interface PropertyLookup {
        @Nullable
        String find(@Nullable String key);

        @Nonnull
        String name();
    }
}
