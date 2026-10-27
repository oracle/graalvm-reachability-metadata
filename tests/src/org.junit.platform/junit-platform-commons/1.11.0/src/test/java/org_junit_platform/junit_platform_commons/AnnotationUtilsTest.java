/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_junit_platform.junit_platform_commons;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.platform.commons.support.AnnotationSupport;

import static org.assertj.core.api.Assertions.assertThat;

public class AnnotationUtilsTest {

    @Test
    void findsPublicFieldsAnnotatedWithMetaAnnotation() {
        List<Field> fields = AnnotationSupport.findPublicAnnotatedFields(AnnotatedFieldSubject.class,
                CharSequence.class, DomainField.class);

        assertThat(fields).extracting(Field::getName).containsExactly("publicText");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.FIELD, ElementType.ANNOTATION_TYPE })
    public @interface DomainField {
    }

    @DomainField
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface ImportantDomainField {
    }

    public static class AnnotatedFieldSubject {

        @ImportantDomainField
        public String publicText = "matched";

        @ImportantDomainField
        public Integer wrongType = 42;

        public String unannotatedText = "ignored";

        @ImportantDomainField
        private String privateText = "ignored";
    }
}
