/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/// Exercises JCommander's variable-arity option subsystem for a deployment manifest CLI.
public class VariableArityCommandLineTest {
    @Test
    public void collectsAllManifestFilesUntilTheNextOption() {
        ManifestOptions options = new ManifestOptions();
        JCommander commander = JCommander.newBuilder()
                .programName("manifest-deployer")
                .addObject(options)
                .build();

        commander.parse(
                "--manifests", "base.yaml", "region.yaml", "secrets.yaml",
                "--environment", "staging");

        assertEquals(
                List.of("base.yaml", "region.yaml", "secrets.yaml"),
                options.manifests);
        assertEquals("staging", options.environment);
    }

    @Parameters(commandDescription = "Deploy a set of application manifests")
    public static class ManifestOptions {
        @Parameter(
                names = "--manifests",
                description = "manifest files to apply",
                variableArity = true,
                required = true)
        private List<String> manifests;

        @Parameter(
                names = "--environment",
                description = "deployment environment",
                required = true)
        private String environment;
    }
}
