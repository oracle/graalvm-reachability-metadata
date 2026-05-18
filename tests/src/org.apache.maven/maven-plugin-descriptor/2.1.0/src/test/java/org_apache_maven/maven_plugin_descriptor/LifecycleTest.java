/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_maven.maven_plugin_descriptor;

import org.apache.maven.plugin.lifecycle.Lifecycle;
import org.apache.maven.plugin.lifecycle.Phase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class LifecycleTest {
    @Test
    void rejectsNullPhaseWhenAddingPhase() {
        Lifecycle lifecycle = new Lifecycle();

        ClassCastException exception = assertThrows(ClassCastException.class, () -> lifecycle.addPhase(null));

        assertThat(exception).hasMessageContaining(Phase.class.getName());
        assertThat(lifecycle.getPhases()).isEmpty();
    }
}
