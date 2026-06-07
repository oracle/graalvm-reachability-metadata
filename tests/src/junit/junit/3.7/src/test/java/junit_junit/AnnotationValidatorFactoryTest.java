/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit_junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.TestClass;
import org.junit.validator.AnnotationValidator;
import org.junit.validator.AnnotationsValidator;
import org.junit.validator.ValidateWith;

public class AnnotationValidatorFactoryTest {
    private static final String VALIDATED_METHOD_PROPERTY =
            AnnotationValidatorFactoryTest.class.getName() + ".validatedMethod";

    @Test
    void createsAnnotationValidatorForValidatedMethodAnnotation() {
        System.clearProperty(VALIDATED_METHOD_PROPERTY);
        try {
            List<Exception> validationErrors = new AnnotationsValidator()
                    .validateTestClass(new TestClass(ValidatedTestCase.class));

            assertThat(validationErrors).isEmpty();
            assertThat(System.getProperty(VALIDATED_METHOD_PROPERTY)).isEqualTo("passingTest");
        } finally {
            System.clearProperty(VALIDATED_METHOD_PROPERTY);
        }
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    @ValidateWith(RecordingMethodAnnotationValidator.class)
    public @interface ValidatedAnnotation {
    }

    public static class ValidatedTestCase {
        @org.junit.Test
        @ValidatedAnnotation
        public void passingTest() {
        }
    }

    public static class RecordingMethodAnnotationValidator extends AnnotationValidator {
        @Override
        public List<Exception> validateAnnotatedMethod(final FrameworkMethod method) {
            System.setProperty(VALIDATED_METHOD_PROPERTY, method.getName());
            return Collections.emptyList();
        }
    }
}
