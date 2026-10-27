/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_jupiter.junit_jupiter_params;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.shadow.com.univocity.parsers.annotations.Copy;
import org.junit.jupiter.params.shadow.com.univocity.parsers.annotations.helpers.AnnotationHelper;

public class AnnotationRegistryInnerAnnotationAttributesTest {

    @Test
    void findsComposedAnnotationWithScalarAndArrayCopyValues() throws NoSuchFieldException {
        Field field = ComposedMarkerRecord.class.getDeclaredField("value");

        Marker marker = AnnotationHelper.findAnnotation(field, Marker.class);

        assertThat(marker).isNotNull();
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.FIELD, ElementType.ANNOTATION_TYPE})
    public @interface Marker {

        String value() default "marker";
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    @Marker
    public @interface ComposedMarker {

        @Copy(to = Marker.class, property = "value")
        String value() default "primary";

        @Copy(to = Marker.class, property = "value")
        String[] aliases() default {"alias"};
    }

    public static class ComposedMarkerRecord {

        @ComposedMarker
        public String value;
    }
}
