/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_quarkus.quarkus_core_deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.quarkus.deployment.builditem.nativeimage.NativeImageConfigBuildItem;

public class Quarkus_core_deploymentTest {
    @Test
    void nativeImageConfigBuilderCollectsConfigurationEntries() {
        NativeImageConfigBuildItem config = NativeImageConfigBuildItem.builder()
                .addRuntimeInitializedClass("com.example.RuntimeInitialized")
                .addRuntimeReinitializedClass("com.example.RuntimeReinitialized")
                .addResourceBundle("messages.application")
                .addProxyClassDefinition("com.example.Service", "java.lang.AutoCloseable")
                .addNativeImageSystemProperty("quarkus.example.enabled", "true")
                .build();

        assertThat(config.getRuntimeInitializedClasses())
                .containsExactlyInAnyOrder("com.example.RuntimeInitialized", "com.example.RuntimeReinitialized");
        assertThat(config.getRuntimeReinitializedClasses())
                .containsExactlyInAnyOrder("com.example.RuntimeInitialized", "com.example.RuntimeReinitialized");
        assertThat(config.getResourceBundles()).containsExactly("messages.application");
        assertThat(config.getProxyDefinitions()).containsExactly(List.of("com.example.Service", "java.lang.AutoCloseable"));
        assertThat(config.getNativeImageSystemProperties()).containsEntry("quarkus.example.enabled", "true");
        assertThatThrownBy(() -> config.getNativeImageSystemProperties().put("quarkus.example.enabled", "false"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
