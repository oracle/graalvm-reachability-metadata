/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.mongo_java_driver;

import org.bson.types.BasicBSONList;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class StringRangeSetTest {
    @Test
    void keySetCreatesTypedArrayWhenProvidedArrayIsTooSmall() {
        BasicBSONList list = new BasicBSONList();
        list.put(2, "last");
        Set<String> keys = list.keySet();

        String[] actualKeys = keys.toArray(new String[0]);

        assertThat(actualKeys).containsExactly("0", "1", "2");
        assertThat(actualKeys).isInstanceOf(String[].class);
    }
}
