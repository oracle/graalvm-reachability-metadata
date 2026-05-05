/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mongodb.bson;

import org.bson.types.BasicBSONList;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class StringRangeSetTest {
    @Test
    void keySetCanCreateTypedArrayForAllListIndexes() {
        final BasicBSONList list = new BasicBSONList();
        list.put(0, "zero");
        list.put(3, "three");

        final Set<String> keys = list.keySet();
        final String[] keyArray = keys.toArray(new String[0]);

        assertThat(keyArray).containsExactly("0", "1", "2", "3");
    }
}
