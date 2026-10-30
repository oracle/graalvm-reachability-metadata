/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_api_plugin;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.api.plugin.descriptor.lifecycle.Lifecycle;
import org.apache.maven.api.plugin.descriptor.lifecycle.LifecycleConfiguration;
import org.apache.maven.api.plugin.descriptor.lifecycle.Phase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LifecycleConfigurationTest {
    @Test
    void buildsLifecycleDocumentAndCopiesItsMappings() {
        Lifecycle lifecycle = Lifecycle.newBuilder().id("custom")
                .phases(List.of(Phase.newBuilder().id("verify").build())).build();
        List<Lifecycle> lifecycles = new ArrayList<>(List.of(lifecycle));
        LifecycleConfiguration configuration = LifecycleConfiguration.newBuilder()
                .namespaceUri("urn:example:lifecycles")
                .modelEncoding("UTF-16")
                .lifecycles(lifecycles)
                .build();
        lifecycles.clear();

        assertThat(configuration.getNamespaceUri()).isEqualTo("urn:example:lifecycles");
        assertThat(configuration.getModelEncoding()).isEqualTo("UTF-16");
        assertThat(configuration.getLifecycles()).containsExactly(lifecycle);
        assertThat(configuration.getLifecycles().get(0).getPhases().get(0).getEffectiveId()).isEqualTo("verify");
        assertThatThrownBy(() -> configuration.getLifecycles().add(lifecycle))
                .isInstanceOf(UnsupportedOperationException.class);

        Lifecycle alternate = lifecycle.withId("alternate");
        LifecycleConfiguration replaced = configuration.withLifecycles(List.of(alternate));
        assertThat(replaced.getLifecycles()).containsExactly(alternate);
        assertThat(replaced.getNamespaceUri()).isEqualTo(configuration.getNamespaceUri());
        assertThat(replaced.getModelEncoding()).isEqualTo("UTF-16");
        assertThat(configuration.getLifecycles()).containsExactly(lifecycle);
        assertThat(LifecycleConfiguration.newInstance().getModelEncoding()).isEqualTo("UTF-8");
        assertThat(LifecycleConfiguration.newInstance().getLifecycles()).isEmpty();
    }
}
