/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_plugin;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.api.plugin.descriptor.MojoDescriptor;
import org.apache.maven.api.plugin.descriptor.Parameter;
import org.apache.maven.api.plugin.descriptor.Resolution;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class MojoDescriptorTest {
    @Test
    void describesGoalExecutionAndCopiesInjectionLists() {
        Parameter output = Parameter.newBuilder().name("outputDirectory").type("java.io.File").build();
        Resolution dependencies = Resolution.newBuilder()
                .field("dependencies").pathScope("compile").requestType("resolve").build();
        List<Parameter> parameters = new ArrayList<>(List.of(output));
        List<Resolution> resolutions = new ArrayList<>(List.of(dependencies));
        MojoDescriptor mojo = MojoDescriptor.newBuilder()
                .goal("compile")
                .description("Compile generated sources")
                .implementation("example.plugins.CompileMojo")
                .phase("compile")
                .executePhase("generate-sources")
                .executeGoal("generate")
                .executeLifecycle("custom")
                .dependencyResolution("compile")
                .dependencyCollection("test")
                .id("sample:compile")
                .fullGoalName("example.plugins:sample-plugin:compile")
                .parameters(parameters)
                .resolutions(resolutions)
                .build();
        parameters.clear();
        resolutions.clear();

        assertThat(mojo.getGoal()).isEqualTo("compile");
        assertThat(mojo.getDescription()).isEqualTo("Compile generated sources");
        assertThat(mojo.getImplementation()).isEqualTo("example.plugins.CompileMojo");
        assertThat(mojo.getLanguage()).isEqualTo("java");
        assertThat(mojo.getPhase()).isEqualTo("compile");
        assertThat(mojo.getExecutePhase()).isEqualTo("generate-sources");
        assertThat(mojo.getExecuteGoal()).isEqualTo("generate");
        assertThat(mojo.getExecuteLifecycle()).isEqualTo("custom");
        assertThat(mojo.getDependencyResolution()).isEqualTo("compile");
        assertThat(mojo.getDependencyCollection()).isEqualTo("test");
        assertThat(mojo.getId()).isEqualTo("sample:compile");
        assertThat(mojo.getFullGoalName()).isEqualTo("example.plugins:sample-plugin:compile");
        assertThat(mojo.getParameters()).containsExactly(output);
        assertThat(mojo.getResolutions()).containsExactly(dependencies);
        assertThatThrownBy(() -> mojo.getParameters().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> mojo.getResolutions().clear()).isInstanceOf(UnsupportedOperationException.class);

        MojoDescriptor report = mojo.with().goal("report").projectRequired(false)
                .directInvocationOnly(true).onlineRequired(true).aggregator(true).inheritedByDefault(false).build();
        assertThat(report.getGoal()).isEqualTo("report");
        assertThat(report.isProjectRequired()).isFalse();
        assertThat(report.isDirectInvocationOnly()).isTrue();
        assertThat(report.isOnlineRequired()).isTrue();
        assertThat(report.isAggregator()).isTrue();
        assertThat(report.isInheritedByDefault()).isFalse();
        assertThat(report.getParameters()).containsExactly(output);
        assertThat(report.getResolutions()).containsExactly(dependencies);
        assertThat(mojo.getGoal()).isEqualTo("compile");
        assertThat(mojo.isProjectRequired()).isTrue();
        assertThat(mojo.isDirectInvocationOnly()).isFalse();
        assertThat(mojo.isOnlineRequired()).isFalse();
        assertThat(mojo.isAggregator()).isFalse();
        assertThat(mojo.isInheritedByDefault()).isTrue();
    }
}
