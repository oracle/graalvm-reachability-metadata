/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_plugin;

import org.apache.maven.api.plugin.descriptor.Resolution;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ResolutionTest {
    @Test
    void describesDependencyResolutionAndDerivesCollectionRequest() {
        Resolution resolution = Resolution.newBuilder()
                .field("compileDependencies").pathScope("compile").requestType("resolve").build();
        Resolution collection = resolution.withRequestType("collect");
        Resolution runtime = resolution.with().field("runtimeDependencies").pathScope("runtime").build();

        assertThat(resolution.getField()).isEqualTo("compileDependencies");
        assertThat(resolution.getPathScope()).isEqualTo("compile");
        assertThat(resolution.getRequestType()).isEqualTo("resolve");
        assertThat(collection.getRequestType()).isEqualTo("collect");
        assertThat(collection.getField()).isEqualTo(resolution.getField());
        assertThat(collection.getPathScope()).isEqualTo("compile");
        assertThat(runtime.getField()).isEqualTo("runtimeDependencies");
        assertThat(runtime.getPathScope()).isEqualTo("runtime");
        assertThat(runtime.getRequestType()).isEqualTo("resolve");
    }
}
