/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_beust.jcommander;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import com.beust.jcommander.DynamicParameter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/// Exercises JCommander's command dispatch, typed conversion, and option validation subsystem.
public class DeploymentCommandLineTest {
    @Test
    public void parsesDeploymentCommandUsedByReleaseAutomation() {
        DeploymentOptions options = new DeploymentOptions();
        JCommander commander = JCommander.newBuilder()
                .programName("release-tool")
                .addCommand("deploy", options)
                .build();

        commander.parse(
                "deploy",
                "--target", "staging/us-east-1",
                "--replicas", "3",
                "--label", "owner=payments",
                "--label", "tier=api",
                "--component", "gateway", "worker");

        assertEquals("deploy", commander.getParsedCommand());
        assertEquals(new DeploymentTarget("staging", "us-east-1"), options.target);
        assertEquals(3, options.replicas);
        assertEquals(Map.of("owner", "payments", "tier", "api"), options.labels);
        assertEquals(List.of("gateway", "worker"), options.components);
    }

    @Test
    public void rejectsAnInvalidReplicaCountBeforeBuildingDeploymentPlan() {
        DeploymentOptions options = new DeploymentOptions();
        JCommander commander = JCommander.newBuilder()
                .addCommand("deploy", options)
                .build();

        ParameterException failure = assertThrows(
                ParameterException.class,
                () -> commander.parse("deploy", "--target", "prod/eu-west-1", "--replicas", "0"));

        assertEquals("--replicas must be positive", failure.getMessage());
    }

    @Parameters(commandDescription = "Deploy a release to a target environment")
    public static class DeploymentOptions {
        @Parameter(
                names = "--target",
                description = "environment and region separated by a slash",
                required = true,
                converter = DeploymentTargetConverter.class)
        private DeploymentTarget target;

        @Parameter(
                names = "--replicas",
                description = "number of application replicas",
                required = true,
                validateWith = PositiveReplicaValidator.class)
        private int replicas;

        @DynamicParameter(
                names = "--label",
                description = "deployment labels in name=value form",
                assignment = "=")
        private Map<String, String> labels = new LinkedHashMap<>();

        @Parameter(
                names = "--component",
                description = "components included in the release",
                variableArity = true)
        private List<String> components = new ArrayList<>();
    }

    public static class DeploymentTarget {
        private final String environment;
        private final String region;

        public DeploymentTarget(String environment, String region) {
            this.environment = environment;
            this.region = region;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof DeploymentTarget)) {
                return false;
            }
            DeploymentTarget target = (DeploymentTarget) other;
            return environment.equals(target.environment) && region.equals(target.region);
        }

        @Override
        public int hashCode() {
            return 31 * environment.hashCode() + region.hashCode();
        }
    }

    public static class DeploymentTargetConverter implements IStringConverter<DeploymentTarget> {
        @Override
        public DeploymentTarget convert(String value) {
            String[] parts = value.split("/", -1);
            if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
                throw new ParameterException("Target must use environment/region form");
            }
            return new DeploymentTarget(parts[0], parts[1]);
        }
    }

    public static class PositiveReplicaValidator implements IParameterValidator {
        @Override
        public void validate(String name, String value) throws ParameterException {
            if (Integer.parseInt(value) < 1) {
                throw new ParameterException(name + " must be positive");
            }
        }
    }
}
