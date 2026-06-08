/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.anakia.NodeList;
import org.jdom.Element;
import org.junit.jupiter.api.Test;

public class NodeListTest {
    @Test
    void cloneCreatesAnIndependentBackingList() throws Exception {
        Element root = new Element("root");
        NodeList original = new NodeList(root);

        NodeList cloned = (NodeList) original.clone();
        original.add(new Element("child"));

        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned.getList()).isNotSameAs(original.getList());
        assertThat(cloned.getList()).containsExactly(root);
        assertThat(original.getList()).hasSize(2);
    }
}
