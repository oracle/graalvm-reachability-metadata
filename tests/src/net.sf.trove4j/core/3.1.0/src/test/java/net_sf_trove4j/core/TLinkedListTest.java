/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package net_sf_trove4j.core;

import gnu.trove.list.TLinkable;
import gnu.trove.list.linked.TLinkedList;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class TLinkedListTest {
    @Test
    void toUnlinkedArrayCreatesTypedArrayAndClearsLinks() {
        TLinkedList<LinkedNode> list = new TLinkedList<>();
        LinkedNode first = new LinkedNode("first");
        LinkedNode second = new LinkedNode("second");

        list.add(first);
        list.add(second);

        LinkedNode[] result = list.toUnlinkedArray(new LinkedNode[0]);

        assertThat(result).containsExactly(first, second);
        assertThat(result.getClass()).isEqualTo(LinkedNode[].class);
        assertThat(list).isEmpty();
        assertThat(first.getPrevious()).isNull();
        assertThat(first.getNext()).isNull();
        assertThat(second.getPrevious()).isNull();
        assertThat(second.getNext()).isNull();
    }

    private static final class LinkedNode implements TLinkable<LinkedNode> {
        private static final long serialVersionUID = 1L;

        private final String value;
        private LinkedNode previous;
        private LinkedNode next;

        private LinkedNode(String value) {
            this.value = value;
        }

        @Override
        public LinkedNode getNext() {
            return next;
        }

        @Override
        public LinkedNode getPrevious() {
            return previous;
        }

        @Override
        public void setNext(LinkedNode linkable) {
            next = linkable;
        }

        @Override
        public void setPrevious(LinkedNode linkable) {
            previous = linkable;
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
