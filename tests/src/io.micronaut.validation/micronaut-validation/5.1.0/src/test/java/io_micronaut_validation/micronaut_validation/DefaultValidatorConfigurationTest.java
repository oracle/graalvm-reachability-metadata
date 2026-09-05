/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_micronaut_validation.micronaut_validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.validation.validator.DefaultValidatorConfiguration;
import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.valueextraction.ExtractedValue;
import jakarta.validation.valueextraction.ValueExtractor;
import org.junit.jupiter.api.Test;

@MicronautTest(startApplication = false)
public class DefaultValidatorConfigurationTest {

    @Inject
    DefaultValidatorConfiguration configuration;

    @Inject
    Validator validator;

    @Test
    void validatesAContainerElementWithAProgrammaticallyRegisteredExtractor() {
        configuration.addValueExtractor(new BoxValueExtractor());

        Set<ConstraintViolation<Shipment>> violations = validator.validate(new Shipment(new Box<>("")));

        assertThat(violations).hasSize(1);
        ConstraintViolation<Shipment> violation = violations.iterator().next();
        assertThat(violation.getPropertyPath().toString()).contains("trackingCode");
        assertThat(violation.getInvalidValue()).isEqualTo("");
        assertThat(validator.validate(new Shipment(new Box<>("ABC-123")))).isEmpty();
    }

    public record Box<T>(T value) {
    }

    private static final class BoxValueExtractor implements ValueExtractor<Box<@ExtractedValue ?>> {

        @Override
        public void extractValues(Box<?> originalValue, ValueReceiver receiver) {
            receiver.value("<box value>", originalValue.value());
        }
    }

    @Introspected
    public static final class Shipment {

        private final Box<String> trackingCode;

        public Shipment(Box<String> trackingCode) {
            this.trackingCode = trackingCode;
        }

        public Box<@NotBlank String> getTrackingCode() {
            return trackingCode;
        }
    }
}
