/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_openjdk_jol.jol_core;

import org.junit.jupiter.api.Test;
import org.openjdk.jol.info.GraphPathRecord;
import org.openjdk.jol.info.GraphWalker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class GraphWalkerTest {

    @Test
    void walksDeclaredAndInheritedReferenceFields() {
        Object ownReference = new Object();
        Object inheritedReference = new Object();
        GraphNode root = new GraphNode(ownReference, inheritedReference);
        List<GraphPathRecord> records = new ArrayList<>();
        GraphWalker walker = new GraphWalker(root);

        walker.addVisitor(records::add);
        walker.walk();

        Map<String, GraphPathRecord> recordsByPath = recordsByPath(records);
        assertThat(recordsByPath.keySet()).containsExactlyInAnyOrder("", ".ownReference", ".inheritedReference");
        assertThat(recordsByPath.get("").obj()).isSameAs(root);
        assertThat(recordsByPath.get("").depth()).isZero();
        assertThat(recordsByPath.get(".ownReference").obj()).isSameAs(ownReference);
        assertThat(recordsByPath.get(".ownReference").depth()).isEqualTo(1);
        assertThat(recordsByPath.get(".inheritedReference").obj()).isSameAs(inheritedReference);
        assertThat(recordsByPath.get(".inheritedReference").depth()).isEqualTo(1);
    }

    private static Map<String, GraphPathRecord> recordsByPath(List<GraphPathRecord> records) {
        Map<String, GraphPathRecord> recordsByPath = new LinkedHashMap<>();
        for (GraphPathRecord record : records) {
            recordsByPath.put(record.path(), record);
        }
        return recordsByPath;
    }

    private static class GraphBaseNode {
        private final Object inheritedReference;

        GraphBaseNode(Object inheritedReference) {
            this.inheritedReference = inheritedReference;
        }
    }

    private static final class GraphNode extends GraphBaseNode {
        private static final Object STATIC_REFERENCE = new Object();

        private final Object ownReference;
        private final int primitiveValue = 42;

        GraphNode(Object ownReference, Object inheritedReference) {
            super(inheritedReference);
            this.ownReference = ownReference;
        }
    }
}
