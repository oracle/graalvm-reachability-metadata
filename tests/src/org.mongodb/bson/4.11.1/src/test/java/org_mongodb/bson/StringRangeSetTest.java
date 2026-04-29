/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.bson.types.BasicBSONList;
import org.junit.jupiter.api.Test;

public class StringRangeSetTest {
    @Test
    void copiesKeysIntoNewTypedArrayWhenProvidedArrayIsTooSmall() {
        BasicBSONList list = new BasicBSONList();
        list.add("first");
        list.add("second");
        list.add("third");
        Set<String> keys = list.keySet();

        String[] destination = new String[0];

        String[] copiedKeys = keys.toArray(destination);

        assertThat(copiedKeys).isNotSameAs(destination);
        assertThat(copiedKeys).containsExactly("0", "1", "2");
    }
}
