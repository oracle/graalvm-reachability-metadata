/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_core_deployment;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import io.quarkus.deployment.Capabilities;
import io.quarkus.deployment.Capability;

public class CapabilitiesTest {
    @Test
    void reportsExactCapabilityPresenceAndAbsence() {
        Capabilities capabilities = new Capabilities(Set.of(Capability.CDI, Capability.REST));

        assertThat(capabilities.getCapabilities()).containsExactlyInAnyOrder(Capability.CDI, Capability.REST);
        assertThat(capabilities.isPresent(Capability.CDI)).isTrue();
        assertThat(capabilities.isMissing(Capability.JSONB)).isTrue();
        assertThat(capabilities.isMissing(Capability.REST)).isFalse();
    }

    @Test
    void reportsCapabilityPrefixesForNestedCapabilities() {
        Capabilities capabilities = new Capabilities(Set.of(
                "com.example.extension.alpha",
                "com.example.other"));

        assertThat(capabilities.isCapabilityWithPrefixPresent("com")).isTrue();
        assertThat(capabilities.isCapabilityWithPrefixPresent("com.example")).isTrue();
        assertThat(capabilities.isCapabilityWithPrefixPresent("com.example.extension")).isTrue();
        assertThat(capabilities.isCapabilityWithPrefixPresent("com.example.extension.alpha")).isTrue();
        assertThat(capabilities.isCapabilityWithPrefixMissing("com.example.extension.beta")).isTrue();
        assertThat(capabilities.isCapabilityWithPrefixMissing("org.example")).isTrue();
    }
}
