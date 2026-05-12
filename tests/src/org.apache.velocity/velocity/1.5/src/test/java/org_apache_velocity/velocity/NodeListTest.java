/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.velocity.anakia.NodeList;
import org.junit.jupiter.api.Test;

public class NodeListTest {
    @Test
    void cloneCreatesIndependentNodeListWithCopiedBackingList() throws Exception {
        NodeList original = new NodeList();
        String firstElement = "first";
        String secondElement = "second";
        original.add(firstElement);
        original.add(secondElement);

        NodeList cloned = (NodeList) original.clone();

        assertThat(cloned).isNotSameAs(original);
        assertThat(cloned).isEqualTo(original);
        assertThat(cloned.getList()).isNotSameAs(original.getList());
        assertThat(cloned.get(0)).isSameAs(firstElement);

        cloned.remove(secondElement);

        assertThat(cloned).containsExactly(firstElement);
        assertThat(original).containsExactly(firstElement, secondElement);
    }
}
