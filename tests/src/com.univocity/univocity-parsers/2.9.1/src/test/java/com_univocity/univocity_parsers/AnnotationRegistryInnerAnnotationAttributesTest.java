/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_univocity.univocity_parsers;

import com.univocity.parsers.annotations.Copy;
import com.univocity.parsers.annotations.Parsed;
import com.univocity.parsers.annotations.helpers.AnnotationHelper;
import com.univocity.parsers.annotations.helpers.AnnotationRegistry;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AnnotationRegistryInnerAnnotationAttributesTest {
    @Test
    public void promotesCopiedScalarAttributeToArrayWhenParentAnnotationRequiresArray() throws NoSuchFieldException {
        AnnotationRegistry.reset();
        final Field field = RecordWithForwardedName.class.getDeclaredField("accountId");

        final Parsed parsed = AnnotationHelper.findAnnotation(field, Parsed.class);

        assertNotNull(parsed);
        assertArrayEquals(
                new String[] {"account_id"},
                AnnotationRegistry.getValue(field, parsed, "field", parsed.field())
        );
    }

    @Parsed
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ANNOTATION_TYPE, FIELD, METHOD})
    public @interface ParsedFieldBridge {
        @Copy(to = Parsed.class, property = "field")
        String[] bridgeFieldNames() default {"ignored_by_registry_promotion"};
    }

    @ParsedFieldBridge
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ANNOTATION_TYPE, FIELD, METHOD})
    public @interface IntermediateParsedField {
    }

    @IntermediateParsedField
    @Retention(RetentionPolicy.RUNTIME)
    @Target({FIELD, METHOD})
    public @interface ScalarParsedField {
        @Copy(to = IntermediateParsedField.class, property = "field")
        String field() default "";
    }

    public static class RecordWithForwardedName {
        @ScalarParsedField(field = "account_id")
        private String accountId;
    }
}
