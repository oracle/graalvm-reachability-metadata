/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package info_picocli.picocli;

import org.junit.jupiter.api.Test;

import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

import static org.assertj.core.api.Assertions.assertThat;

public class CommandLineInnerModelInnerCommandSpecTest {
    private static final String BUNDLE_NAME = "info_picocli.picocli.CommandLineInnerModelInnerCommandSpecTestMessages";

    @Test
    void resourceBundleBaseNameLoadsBundleAndUpdatesCommandArguments() {
        CommandSpec spec = CommandSpec.create().name("tool");
        OptionSpec verbose = OptionSpec.builder("--verbose")
                .description("fallback option description")
                .descriptionKey("verbose.description")
                .build();
        PositionalParamSpec file = PositionalParamSpec.builder()
                .index("0")
                .description("fallback file description")
                .descriptionKey("file.description")
                .build();
        spec.addOption(verbose);
        spec.addPositional(file);

        CommandSpec returned = spec.resourceBundleBaseName(BUNDLE_NAME);

        assertThat(returned).isSameAs(spec);
        assertThat(spec.resourceBundleBaseName()).isEqualTo(BUNDLE_NAME);
        assertThat(spec.resourceBundle()).isNotNull();
        assertThat(spec.resourceBundle().getString("verbose.description")).isEqualTo("Enable detailed output");
        assertThat(verbose.description()).containsExactly("Enable detailed output");
        assertThat(file.description()).containsExactly("Input file path");
    }
}
