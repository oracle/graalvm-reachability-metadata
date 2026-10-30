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
import org.apache.maven.api.plugin.descriptor.lifecycle.Phase;
import org.apache.maven.api.xml.XmlNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class PhaseTest {
    @Test
    void formatsEffectivePhaseIdentityWithExecutionPointAndPriority() {
        Phase phase = Phase.newBuilder().id("integration-test").build();
        Phase prioritized = phase.withPriority(100);
        Phase after = phase.withExecutionPoint("after");
        Phase before = phase.with().executionPoint("before").priority(-10).build();

        assertThat(phase.getEffectiveId()).isEqualTo("integration-test");
        assertThat(prioritized.getEffectiveId()).isEqualTo("integration-test[100]");
        assertThat(after.getEffectiveId()).isEqualTo("after:integration-test");
        assertThat(after.withPriority(100).getEffectiveId()).isEqualTo("after:integration-test[100]");
        assertThat(before.getEffectiveId()).isEqualTo("before:integration-test[-10]");
        assertThat(before.getId()).isEqualTo("integration-test");
        assertThat(before.getExecutionPoint()).isEqualTo("before");
        assertThat(before.getPriority()).isEqualTo(-10);
        assertThat(phase.getExecutionPoint()).isNull();
        assertThat(phase.getPriority()).isZero();
    }

    @Test
    void snapshotsExecutionsAndPreservesConfigurationWhenDerivingPhase() {
        Execution execution = Execution.newBuilder().goals(List.of("prepare", "verify")).build();
        List<Execution> executions = new ArrayList<>(List.of(execution));
        XmlNode configuration = XmlNode.newBuilder().name("configuration")
                .children(List.of(XmlNode.newInstance("skip", "false"))).build();
        Phase phase = Phase.newBuilder().id("verify").executions(executions).configuration(configuration).build();
        executions.clear();
        Phase renamed = phase.withId("test");

        assertThat(phase.getExecutions()).containsExactly(execution);
        assertThat(phase.getConfiguration().child("skip").value()).isEqualTo("false");
        assertThatThrownBy(() -> phase.getExecutions().add(execution))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThat(renamed.getId()).isEqualTo("test");
        assertThat(renamed.getConfiguration()).isSameAs(configuration);
        assertThat(renamed.getExecutions()).containsExactly(execution);
        assertThat(phase.getId()).isEqualTo("verify");
    }
}
