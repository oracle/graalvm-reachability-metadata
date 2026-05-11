/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.IParameterValidator2;
import com.beust.jcommander.IValueValidator;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterDescription;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.SubParameter;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParameterDescriptionTest {
    @Test
    void parsesOrderedSubParametersIntoValueObject() {
        CoordinateCommand command = new CoordinateCommand();

        new JCommander(command, "--coordinate", "10", "20");

        assertThat(command.coordinate).isNotNull();
        assertThat(command.coordinate.x).isEqualTo("10");
        assertThat(command.coordinate.y).isEqualTo("20");
    }

    @Test
    void invokesParameterAndValueValidatorsDeclaredOnParameter() {
        RecordingParameterValidator.invocations.set(0);
        RecordingParameterValidator.contextInvocations.set(0);
        RecordingValueValidator.invocations.set(0);
        ValidatedCommand command = new ValidatedCommand();

        new JCommander(command, "--name", "jcommander");

        assertThat(command.name).isEqualTo("jcommander");
        assertThat(RecordingParameterValidator.invocations.get()).isEqualTo(1);
        assertThat(RecordingParameterValidator.contextInvocations.get()).isEqualTo(1);
        assertThat(RecordingValueValidator.invocations.get()).isEqualTo(2);
    }

    @Test
    void readsParameterDescriptionsFromParametersResourceBundle() {
        ParametersBundleCommand command = new ParametersBundleCommand();
        JCommander commander = new JCommander(command);

        ParameterDescription description = parameterDescription(commander, "--from-parameters");

        assertThat(description.getDescription()).isEqualTo("description loaded from @Parameters");
    }

    @Test
    @SuppressWarnings("deprecation")
    void readsParameterDescriptionsFromDeprecatedResourceBundleAnnotation() {
        DeprecatedBundleCommand command = new DeprecatedBundleCommand();
        JCommander commander = new JCommander(command);

        ParameterDescription description = parameterDescription(
                commander,
                "--from-deprecated-bundle");

        assertThat(description.getDescription())
                .isEqualTo("description loaded from @ResourceBundle");
    }

    private static ParameterDescription parameterDescription(JCommander commander, String name) {
        return commander.getParameters().stream()
                .filter(description -> description.getNames().contains(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing parameter description for " + name));
    }

    public static class CoordinateCommand {
        @Parameter(names = "--coordinate", arity = 2)
        private Coordinate coordinate;
    }

    public static class Coordinate {
        @SubParameter(order = 0)
        public String x;

        @SubParameter(order = 1)
        public String y;
    }

    public static class ValidatedCommand {
        @Parameter(
                names = "--name",
                validateWith = RecordingParameterValidator.class,
                validateValueWith = RecordingValueValidator.class)
        private String name;
    }

    public static class RecordingParameterValidator implements IParameterValidator2 {
        private static final AtomicInteger invocations = new AtomicInteger();
        private static final AtomicInteger contextInvocations = new AtomicInteger();

        @Override
        public void validate(String name, String value) throws ParameterException {
            assertThat(name).isEqualTo("--name");
            assertThat(value).isEqualTo("jcommander");
            invocations.incrementAndGet();
        }

        @Override
        public void validate(String name, String value, ParameterDescription parameterDescription)
                throws ParameterException {
            assertThat(name).isEqualTo("--name");
            assertThat(value).isEqualTo("jcommander");
            assertThat(parameterDescription.getNames()).contains("--name");
            contextInvocations.incrementAndGet();
        }
    }

    public static class RecordingValueValidator implements IValueValidator<String> {
        private static final AtomicInteger invocations = new AtomicInteger();

        @Override
        public void validate(String name, String value) throws ParameterException {
            assertThat(name).isEqualTo("--name");
            assertThat(value).isEqualTo("jcommander");
            invocations.incrementAndGet();
        }
    }

    @Parameters(resourceBundle = "com_beust.jcommander.parameter_descriptions")
    public static class ParametersBundleCommand {
        @Parameter(names = "--from-parameters", descriptionKey = "parameters.description")
        private String value;
    }

    @com.beust.jcommander.ResourceBundle("com_beust.jcommander.parameter_descriptions")
    public static class DeprecatedBundleCommand {
        @Parameter(names = "--from-deprecated-bundle", descriptionKey = "deprecated.description")
        private String value;
    }
}
