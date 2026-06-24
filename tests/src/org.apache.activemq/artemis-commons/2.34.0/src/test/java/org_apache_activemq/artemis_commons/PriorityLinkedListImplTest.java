/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_activemq.artemis_commons;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.activemq.artemis.utils.collections.PriorityLinkedListImpl;
import org.junit.jupiter.api.Test;

public class PriorityLinkedListImplTest {
    @Test
    public void createsPriorityLevelsAndPollsHighestPriorityFirst() {
        PriorityLinkedListImpl<String> list = new PriorityLinkedListImpl<>(3);

        assertThat(list.isEmpty()).isTrue();
        assertThat(list.size()).isZero();

        list.addTail("low", 0);
        list.addTail("high-tail", 2);
        list.addHead("high-head", 2);
        list.addTail("middle", 1);

        assertThat(list.size()).isEqualTo(4);
        assertThat(list.isEmpty()).isFalse();
        assertThat(list.poll()).isEqualTo("high-head");
        assertThat(list.poll()).isEqualTo("high-tail");
        assertThat(list.poll()).isEqualTo("middle");
        assertThat(list.poll()).isEqualTo("low");
        assertThat(list.poll()).isNull();
        assertThat(list.isEmpty()).isTrue();
    }
}
