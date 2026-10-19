/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_alibaba.dubbo;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedList;
import org.junit.jupiter.api.Test;

public class CollectionDeserializerTest {
    @Test
    void restoresConcreteCollections() throws Exception {
        LinkedList<String> source = new LinkedList<String>();
        source.add("one");
        source.add("two");

        Object copy = HessianTestSupport.roundTrip(source);

        assertThat(copy).isInstanceOf(LinkedList.class);
        assertThat(copy).isEqualTo(source);
    }
}
