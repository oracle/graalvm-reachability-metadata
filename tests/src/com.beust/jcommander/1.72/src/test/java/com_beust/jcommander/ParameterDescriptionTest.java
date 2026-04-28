/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.IParameterValidator2;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterDescription;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.SubParameter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ParameterDescriptionTest {
    @Test
    void parsesObjectBackedSubParameters() {
        SubParameterOptions options = new SubParameterOptions();

        JCommander.newBuilder()
                .addObject(options)
                .build()
                .parse("--range", "start", "end");

        assertThat(options.range).isNotNull();
        assertThat(options.range.start).isEqualTo("start");
        assertThat(options.range.end).isEqualTo("end");
    }

    @Test
    void invokesSecondPhaseParameterValidator() {
        TrackingValidator.reset();
        ValidatedOptions options = new ValidatedOptions();

        JCommander.newBuilder()
                .addObject(options)
                .build()
                .parse("--checked", "accepted");

        assertThat(options.checked).isEqualTo("accepted");
        assertThat(TrackingValidator.basicValidationCalls).isEqualTo(1);
        assertThat(TrackingValidator.descriptionValidationCalls).isEqualTo(1);
        assertThat(TrackingValidator.validatedParameterNames).isEqualTo("--checked");
    }

    @Test
    void loadsDescriptionFromParametersResourceBundle() {
        JCommander commander = new JCommander(new ParametersBundleOptions());

        assertThat(commander.getParameters()).hasSize(1);
        assertThat(commander.getParameters().get(0).getDescription())
                .isEqualTo("Description loaded from @Parameters resource bundle");
    }

    @Test
    void loadsDescriptionFromLegacyResourceBundleAnnotation() {
        JCommander commander = new JCommander(new LegacyBundleOptions());

        assertThat(commander.getParameters()).hasSize(1);
        assertThat(commander.getParameters().get(0).getDescription())
                .isEqualTo("Description loaded from legacy @ResourceBundle annotation");
    }

    public static class SubParameterOptions {
        @Parameter(names = "--range", arity = 2)
        private Range range;
    }

    public static class Range {
        @SubParameter(order = 0)
        public String start;

        @SubParameter(order = 1)
        public String end;

        public Range() {
        }
    }

    public static class ValidatedOptions {
        @Parameter(names = "--checked", validateWith = TrackingValidator.class)
        private String checked;
    }

    public static class TrackingValidator implements IParameterValidator2 {
        private static int basicValidationCalls;
        private static int descriptionValidationCalls;
        private static String validatedParameterNames;

        public TrackingValidator() {
        }

        static void reset() {
            basicValidationCalls = 0;
            descriptionValidationCalls = 0;
            validatedParameterNames = null;
        }

        @Override
        public void validate(String name, String value) {
            basicValidationCalls++;
            if (!"--checked".equals(name) || !"accepted".equals(value)) {
                throw new ParameterException("Unexpected parameter value");
            }
        }

        @Override
        public void validate(String name, String value, ParameterDescription pd) {
            descriptionValidationCalls++;
            validatedParameterNames = pd.getNames();
        }
    }

    @Parameters(resourceBundle = "com_beust.jcommander.parameter_descriptions")
    public static class ParametersBundleOptions {
        @Parameter(names = "--localized", descriptionKey = "parameters.description")
        private String value;
    }

    @com.beust.jcommander.ResourceBundle("com_beust.jcommander.parameter_descriptions")
    public static class LegacyBundleOptions {
        @Parameter(names = "--legacy-localized", descriptionKey = "legacy.description")
        private String value;
    }
}
