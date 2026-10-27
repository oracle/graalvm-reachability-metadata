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
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runners.model.TestClass;
import org.junit.validator.AnnotationValidator;
import org.junit.validator.ValidateWith;

public class AnnotationValidatorFactoryTest {

    @Test
    void instantiatesAndRunsAValidatorAttachedToAnAnnotation() {
        RecordingValidator.validatedClassName = null;

        Result result = JUnitCore.runClasses(ValidatedFixture.class);

        assertThat(result.wasSuccessful()).isTrue();
        assertThat(RecordingValidator.validatedClassName).isEqualTo("ValidatedFixture");
    }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @ValidateWith(RecordingValidator.class)
    public @interface Checked {
    }

    @Checked
    public static class ValidatedFixture {
        public ValidatedFixture() {
        }

        @org.junit.Test
        public void execute() {
        }
    }

    public static class RecordingValidator extends AnnotationValidator {
        private static String validatedClassName;

        public RecordingValidator() {
        }

        @Override
        public List<Exception> validateAnnotatedClass(TestClass testClass) {
            validatedClassName = testClass.getJavaClass().getSimpleName();
            return super.validateAnnotatedClass(testClass);
        }
    }
}
