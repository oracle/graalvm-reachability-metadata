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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class GraphWalkerTest {
    @Test
    void walksObjectFieldsDeclaredOnClassAndSuperclass() {
        Leaf inheritedLeaf = new Leaf();
        Leaf declaredLeaf = new Leaf();
        Branch branch = new Branch(inheritedLeaf, declaredLeaf);
        List<GraphPathRecord> records = walk(branch);

        assertThat(records).extracting(GraphPathRecord::obj)
                .contains(branch, inheritedLeaf, declaredLeaf);
        assertThat(records).extracting(GraphPathRecord::path)
                .contains("", ".inheritedLeaf", ".declaredLeaf");
    }

    @Test
    void walksReferencesStoredInObjectArrays() {
        Leaf arrayLeaf = new Leaf();
        ArrayHolder holder = new ArrayHolder(arrayLeaf);
        List<GraphPathRecord> records = walk(holder);

        assertThat(records).extracting(GraphPathRecord::obj)
                .contains(holder, holder.references, arrayLeaf);
        assertThat(records).extracting(GraphPathRecord::path)
                .contains("", ".references", ".references[0]");
    }

    private static List<GraphPathRecord> walk(Object root) {
        List<GraphPathRecord> records = new ArrayList<>();
        GraphWalker walker = new GraphWalker(root);
        walker.addVisitor(records::add);
        walker.walk();
        return records;
    }

    private static class BaseBranch {
        private final Leaf inheritedLeaf;

        BaseBranch(Leaf inheritedLeaf) {
            this.inheritedLeaf = inheritedLeaf;
        }
    }

    private static class Branch extends BaseBranch {
        private static final Leaf STATIC_LEAF = new Leaf();

        private final Leaf declaredLeaf;
        private final int primitiveValue = 42;

        Branch(Leaf inheritedLeaf, Leaf declaredLeaf) {
            super(inheritedLeaf);
            this.declaredLeaf = declaredLeaf;
        }
    }

    private static class ArrayHolder {
        private final Object[] references;

        ArrayHolder(Object firstReference) {
            this.references = new Object[] { firstReference, null };
        }
    }

    private static class Leaf {
    }
}
