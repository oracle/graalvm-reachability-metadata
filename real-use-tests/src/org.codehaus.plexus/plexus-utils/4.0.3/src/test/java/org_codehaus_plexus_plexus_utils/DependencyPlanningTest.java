/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_codehaus_plexus_plexus_utils;

import java.util.List;

import org.codehaus.plexus.util.dag.CycleDetectedException;
import org.codehaus.plexus.util.dag.DAG;
import org.codehaus.plexus.util.dag.TopologicalSorter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Builds a deterministic plugin execution plan from a Plexus dependency graph.
 *
 * A build application records each stage's prerequisites in Plexus Utils and uses the
 * resulting plan to run compilation before packaging, while rejecting cyclic plans.
 */
class DependencyPlanningTest {

    @Test
    void ordersStagesAfterTheirPrerequisites() throws CycleDetectedException {
        DAG buildPlan = new DAG();
        buildPlan.addEdge("package", "resources");
        buildPlan.addEdge("package", "classes");
        buildPlan.addEdge("resources", "compile");
        buildPlan.addEdge("classes", "compile");

        List<String> executionPlan = TopologicalSorter.sort(buildPlan);

        assertThat(executionPlan).containsExactly("compile", "resources", "classes", "package");
    }

    @Test
    void rejectsCyclicStagePlans() throws CycleDetectedException {
        DAG buildPlan = new DAG();
        buildPlan.addEdge("package", "compile");

        assertThatThrownBy(() -> buildPlan.addEdge("compile", "package"))
                .isInstanceOf(CycleDetectedException.class)
                .hasMessageContaining("introduces to cycle");
    }
}
