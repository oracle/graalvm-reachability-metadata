/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_commons.commons_collections4;

import org.apache.commons.collections4.sequence.CommandVisitor;
import org.apache.commons.collections4.sequence.EditScript;
import org.apache.commons.collections4.sequence.SequencesComparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Exercises the sequence-diff subsystem for updating a service deployment plan.
 */
public class DeploymentPlanSequenceTest {
    @Test
    @Timeout(60)
    public void reportsTheMinimalChangesBetweenDeploymentPlans() {
        List<String> runningPlan = List.of("fetch", "validate", "deploy", "restart");
        List<String> requestedPlan = List.of("fetch", "validate", "migrate", "deploy");

        EditScript<String> changes = new SequencesComparator<>(runningPlan, requestedPlan).getScript();
        List<String> operations = new ArrayList<>();
        changes.visit(new CommandVisitor<>() {
            @Override
            public void visitDeleteCommand(String command) {
                operations.add("remove:" + command);
            }

            @Override
            public void visitInsertCommand(String command) {
                operations.add("add:" + command);
            }

            @Override
            public void visitKeepCommand(String command) {
                operations.add("keep:" + command);
            }
        });

        assertEquals(3, changes.getLCSLength());
        assertEquals(2, changes.getModifications());
        assertEquals(
                List.of("keep:fetch", "keep:validate", "add:migrate", "keep:deploy", "remove:restart"),
                operations);
    }
}
