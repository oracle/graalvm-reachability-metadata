/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package junit.junit;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.runners.model.TestClass;
import org.junit.validator.AnnotationValidator;
import org.junit.validator.AnnotationsValidator;
import org.junit.validator.ValidateWith;

public class AnnotationValidatorFactoryTest {
    @Test
    void createsValidatorDeclaredByValidateWithAnnotation() {
        RecordingAnnotationValidator.validatedClasses.clear();

        List<Exception> validationErrors = new AnnotationsValidator()
                .validateTestClass(new TestClass(AnnotatedFixture.class));

        assertThat(validationErrors).isEmpty();
        assertThat(RecordingAnnotationValidator.validatedClasses).containsExactly(AnnotatedFixture.class);
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @ValidateWith(RecordingAnnotationValidator.class)
    public @interface ValidatedByRecordingValidator {
    }

    @ValidatedByRecordingValidator
    public static final class AnnotatedFixture {
    }

    public static final class RecordingAnnotationValidator extends AnnotationValidator {
        private static final List<Class<?>> validatedClasses = new ArrayList<>();

        public RecordingAnnotationValidator() {
        }

        @Override
        public List<Exception> validateAnnotatedClass(TestClass testClass) {
            validatedClasses.add(testClass.getJavaClass());
            return Collections.emptyList();
        }
    }
}
