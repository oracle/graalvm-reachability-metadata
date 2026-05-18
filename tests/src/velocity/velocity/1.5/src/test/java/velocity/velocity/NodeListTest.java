/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.apache.velocity.anakia.NodeList;
import org.junit.jupiter.api.Test;

public class NodeListTest {
    @Test
    void cloneCreatesIndependentBackingList() throws Exception {
        String originalNode = "original";
        NodeList original = new NodeList();
        original.add(originalNode);

        NodeList cloned = (NodeList) original.clone();
        List clonedBackingList = cloned.getList();

        original.add("added");

        assertThat(clonedBackingList).isNotSameAs(original.getList());
        assertThat(cloned).containsExactly(originalNode);
        assertThat(original).hasSize(2);
    }
}
