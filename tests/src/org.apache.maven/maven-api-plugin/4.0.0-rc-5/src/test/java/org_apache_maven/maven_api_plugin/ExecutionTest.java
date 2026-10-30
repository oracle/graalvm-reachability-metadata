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
import org.apache.maven.api.xml.XmlNode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ExecutionTest {
    @Test
    void snapshotsOrderedGoalsAndRetainsTheirConfiguration() {
        List<String> goals = new ArrayList<>(List.of("prepare", "compile", "verify"));
        XmlNode configuration = XmlNode.newBuilder().name("configuration")
                .children(List.of(XmlNode.newInstance("encoding", "UTF-8"))).build();
        Execution execution = Execution.newBuilder().goals(goals).configuration(configuration).build();
        goals.clear();

        assertThat(execution.getGoals()).containsExactly("prepare", "compile", "verify");
        assertThat(execution.getConfiguration().child("encoding").value()).isEqualTo("UTF-8");
        assertThatThrownBy(() -> execution.getGoals().set(0, "clean"))
                .isInstanceOf(UnsupportedOperationException.class);

        Execution verifyOnly = execution.withGoals(List.of("verify"));
        XmlNode alternate = XmlNode.newBuilder().name("configuration")
                .children(List.of(XmlNode.newInstance("encoding", "US-ASCII"))).build();
        Execution reconfigured = execution.with().configuration(alternate).build();
        assertThat(verifyOnly.getGoals()).containsExactly("verify");
        assertThat(verifyOnly.getConfiguration()).isSameAs(configuration);
        assertThat(reconfigured.getConfiguration().child("encoding").value()).isEqualTo("US-ASCII");
        assertThat(reconfigured.getGoals()).containsExactly("prepare", "compile", "verify");
        assertThat(execution.getConfiguration()).isSameAs(configuration);
    }
}
