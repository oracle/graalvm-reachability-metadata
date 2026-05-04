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
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class GraphWalkerTest {
    @Test
    void walksPrivateReferencesDeclaredOnClassAndSuperclass() {
        Leaf child = new Leaf(1);
        Leaf inherited = new Leaf(2);
        RootNode root = new RootNode(child, inherited);
        List<GraphPathRecord> records = new ArrayList<GraphPathRecord>();

        GraphWalker walker = new GraphWalker(root);
        walker.addVisitor(records::add);
        walker.walk();

        Map<Object, String> pathsByObject = pathsByObject(records);
        assertThat(pathsByObject).hasSize(3);
        assertThat(pathsByObject.get(root)).isEqualTo("");
        assertThat(pathsByObject.get(child)).isEqualTo(".child");
        assertThat(pathsByObject.get(inherited)).isEqualTo(".inherited");
        assertThat(pathsByObject).doesNotContainKey(RootNode.STATIC_REFERENCE);
    }

    private static Map<Object, String> pathsByObject(List<GraphPathRecord> records) {
        Map<Object, String> paths = new IdentityHashMap<Object, String>();
        for (GraphPathRecord record : records) {
            paths.put(record.obj(), record.path());
        }
        return paths;
    }

    private static class BaseNode {
        private final Leaf inherited;
        private final int inheritedPrimitive = 7;

        BaseNode(Leaf inherited) {
            this.inherited = inherited;
        }
    }

    private static final class RootNode extends BaseNode {
        private static final Leaf STATIC_REFERENCE = new Leaf(3);

        private final Leaf child;
        private final Leaf nullReference = null;
        private final long primitive = 11L;

        RootNode(Leaf child, Leaf inherited) {
            super(inherited);
            this.child = child;
        }
    }

    private static final class Leaf {
        private final int value;

        Leaf(int value) {
            this.value = value;
        }
    }
}
