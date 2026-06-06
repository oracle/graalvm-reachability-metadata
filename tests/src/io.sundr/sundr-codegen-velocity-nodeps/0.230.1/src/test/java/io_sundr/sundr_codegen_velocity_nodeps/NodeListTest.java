/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.velocity.anakia.NodeList;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class NodeListTest {

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void cloneCopiesBackingListWithPublicNoArgConstructor() throws CloneNotSupportedException {
        List<String> backingList = new ArrayList<>();
        backingList.add("first");
        backingList.add("second");
        NodeList original = new NodeList(backingList, false);

        NodeList clone = (NodeList) original.clone();

        assertThat(clone).isNotSameAs(original);
        assertThat(clone.getList()).isNotSameAs(original.getList());
        assertThat(clone.getList()).containsExactly("first", "second");

        original.add("third");
        clone.add("clone-only");

        assertThat(original.getList()).containsExactly("first", "second", "third");
        assertThat(clone.getList()).containsExactly("first", "second", "clone-only");
    }
}
