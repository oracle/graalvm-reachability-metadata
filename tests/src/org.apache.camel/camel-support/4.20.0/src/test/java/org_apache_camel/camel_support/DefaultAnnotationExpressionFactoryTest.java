/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_camel.camel_support;

import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import org.apache.camel.support.language.DefaultAnnotationExpressionFactory;
import org.apache.camel.support.language.LanguageAnnotation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class DefaultAnnotationExpressionFactoryTest {
    @Test
    void readsExpressionValueFromAnnotation() {
        ValueExpression annotation = AnnotatedExpression.class.getAnnotation(ValueExpression.class);
        ExposingAnnotationExpressionFactory factory = new ExposingAnnotationExpressionFactory();

        String expression = factory.expressionFrom(annotation);

        assertThat(expression).isEqualTo("header.customerId");
    }

    @LanguageAnnotation(language = "test")
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ValueExpression {
        String value();
    }

    @ValueExpression("header.customerId")
    private static final class AnnotatedExpression {
    }

    private static final class ExposingAnnotationExpressionFactory extends DefaultAnnotationExpressionFactory {
        String expressionFrom(Annotation annotation) {
            return getExpressionFromAnnotation(annotation);
        }
    }
}
