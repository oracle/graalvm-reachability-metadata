/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_core;

import org.apache.maven.plugin.PluginConfigurationException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.usability.PluginConfigurationDiagnoser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PluginConfigurationDiagnoserTest {
    @Test
    void recognizesPluginConfigurationFailuresInCausality() {
        PluginConfigurationDiagnoser diagnoser = new PluginConfigurationDiagnoser();
        PluginConfigurationException configurationFailure = new PluginConfigurationException(
                newPluginDescriptor(),
                "Missing required configuration value");

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalStateException("Build failed", configurationFailure));

        assertThat(canDiagnose).isTrue();
    }

    @Test
    void returnsConfigurationFailureMessageWhenNoDetailedCauseIsAvailable() {
        PluginConfigurationDiagnoser diagnoser = new PluginConfigurationDiagnoser();
        PluginConfigurationException configurationFailure = new PluginConfigurationException(
                newPluginDescriptor(),
                "Missing required configuration value");

        String diagnosis = diagnoser.diagnose(new IllegalStateException("Build failed", configurationFailure));

        assertThat(diagnosis).isEqualTo(
                "Error configuring: org.example.plugins:demo-maven-plugin. Reason: "
                        + "Missing required configuration value");
    }

    @Test
    void ignoresUnrelatedFailures() {
        PluginConfigurationDiagnoser diagnoser = new PluginConfigurationDiagnoser();

        boolean canDiagnose = diagnoser.canDiagnose(new IllegalArgumentException("Not a plugin configuration failure"));

        assertThat(canDiagnose).isFalse();
    }

    private static PluginDescriptor newPluginDescriptor() {
        PluginDescriptor descriptor = new PluginDescriptor();
        descriptor.setGroupId("org.example.plugins");
        descriptor.setArtifactId("demo-maven-plugin");
        descriptor.setVersion("1.0");
        descriptor.setGoalPrefix("demo");
        return descriptor;
    }
}
