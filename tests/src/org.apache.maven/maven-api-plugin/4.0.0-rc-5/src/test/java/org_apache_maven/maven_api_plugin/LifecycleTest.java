/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_plugin;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.api.plugin.descriptor.lifecycle.Execution;
import org.apache.maven.api.plugin.descriptor.lifecycle.Lifecycle;
import org.apache.maven.api.plugin.descriptor.lifecycle.Phase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LifecycleTest {
    @Test
    void snapshotsPhaseMappingsAndDerivesAlternateLifecycle() {
        Execution execution = Execution.newBuilder().goals(List.of("compile")).build();
        Phase compile = Phase.newBuilder().id("compile").executions(List.of(execution)).build();
        Phase verify = Phase.newBuilder().id("verify").build();
        List<Phase> phases = new ArrayList<>(List.of(compile, verify));
        Lifecycle lifecycle = Lifecycle.newBuilder().id("custom").phases(phases).build();
        phases.clear();

        assertThat(lifecycle.getId()).isEqualTo("custom");
        assertThat(lifecycle.getPhases()).containsExactly(compile, verify);
        assertThat(lifecycle.getPhases().get(0).getExecutions().get(0).getGoals()).containsExactly("compile");
        assertThatThrownBy(() -> lifecycle.getPhases().clear()).isInstanceOf(UnsupportedOperationException.class);

        Lifecycle renamed = lifecycle.withId("alternate");
        Lifecycle verifyOnly = lifecycle.with().phases(List.of(verify)).build();
        assertThat(renamed.getId()).isEqualTo("alternate");
        assertThat(renamed.getPhases()).containsExactly(compile, verify);
        assertThat(verifyOnly.getId()).isEqualTo("custom");
        assertThat(verifyOnly.getPhases()).containsExactly(verify);
        assertThat(lifecycle.getId()).isEqualTo("custom");
        assertThat(lifecycle.getPhases()).containsExactly(compile, verify);
    }
}
